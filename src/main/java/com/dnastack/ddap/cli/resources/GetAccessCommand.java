package com.dnastack.ddap.cli.resources;

import com.dnastack.ddap.cli.client.dam.DdapFrontendClient;
import com.dnastack.ddap.cli.client.dam.ViewAccessTokenResponse;
import com.dnastack.ddap.cli.login.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.AllArgsConstructor;

import static com.dnastack.ddap.cli.HttpUtil.parseDdapErrorMessage;
import static java.lang.String.format;

@AllArgsConstructor
public class GetAccessCommand {
    private final Context context;
    private final DdapFrontendClient ddapFrontendClient;
    private final ObjectMapper objectMapper;

    public static class GetAccessException extends Exception {
        GetAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public ViewAccessTokenResponse getAccessToken(String resourceId, String viewId, String ttl) throws GetAccessException {
        try {
            return ddapFrontendClient.getAccessToken(context.getRealm(),
                                                     context.getTokens().getIdToken(),
                                                     resourceId,
                                                     viewId,
                                                     ttl);
        } catch (FeignException fe) {
            final String message = parseDdapErrorMessage(objectMapper, fe);
            throw new GetAccessException(format("Could not get access to %s/%s\n%d : %s\n",
                                                resourceId,
                                                viewId,
                                                fe.status(),
                                                message), fe);
        }
    }
}
