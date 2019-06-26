package com.dnastack.ddap.cli.resources;

import com.dnastack.ddap.cli.client.dam.DdapFrontendClient;
import com.dnastack.ddap.cli.client.dam.ResourceResponse;
import com.dnastack.ddap.cli.login.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.AllArgsConstructor;

import static com.dnastack.ddap.cli.HttpUtil.parseDdapErrorMessage;
import static java.lang.String.format;

@AllArgsConstructor
public class ListCommand {
    private final Context context;
    private final DdapFrontendClient ddapFrontendClient;
    private final ObjectMapper objectMapper;

    public static class ListException extends Exception {
        ListException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public ResourceResponse listResources() throws ListException {
        try {
            return ddapFrontendClient.getResources(context.getRealm());
        } catch (FeignException fe) {
            final String message = parseDdapErrorMessage(objectMapper, fe);
            throw new ListException(format("Could not list resources\n%d : %s\n", fe.status(), message), fe);
        }
    }
}
