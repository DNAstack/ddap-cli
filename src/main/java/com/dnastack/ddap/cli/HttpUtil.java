package com.dnastack.ddap.cli;

import com.dnastack.ddap.cli.client.dam.DdapErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Response;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.Collections;

public class HttpUtil {
    public static boolean hasAuthorization(Response response) {
        return !response.headers().getOrDefault("Authorization", Collections.emptyList()).isEmpty();
    }

    public static boolean hasLocation(Response response) {
        return !response.headers().getOrDefault("Location", Collections.emptyList()).isEmpty();
    }

    public static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    public static String parseDdapErrorMessage(ObjectMapper objectMapper, FeignException fe) {
        if (fe.content() == null) {
            return "";
        }

        try {
            return objectMapper.readValue(fe.content(), DdapErrorResponse.class).getMessage();
        } catch (IOException e) {
            return fe.contentUTF8();
        }
    }

    public static String parseDdapErrorMessage(ObjectMapper objectMapper, Response.Body body) {
        String rawString = null;
        if (body == null) {
            return "";
        }

        try {
            rawString = IOUtils.toString(body.asInputStream());
            return objectMapper.readValue(rawString, DdapErrorResponse.class).getMessage();
        } catch (IOException e) {
            if (rawString != null) {
                return rawString;
            } else {
                return "";
            }
        }
    }
}
