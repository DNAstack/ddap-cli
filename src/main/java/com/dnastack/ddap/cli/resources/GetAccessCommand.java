package com.dnastack.ddap.cli.resources;

import com.dnastack.ddap.cli.client.dam.*;
import com.dnastack.ddap.cli.client.dam.model.DamInfo;
import com.dnastack.ddap.cli.client.dam.model.ResourceTokens;
import com.dnastack.ddap.cli.login.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.AllArgsConstructor;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.dnastack.ddap.cli.client.HttpUtil.parseDdapErrorMessage;
import static java.lang.String.format;

@AllArgsConstructor
public class GetAccessCommand {

    private static final int TIMEOUT_IN_SECONDS = 10 * 60;
    private static final int INTERVAL_IN_SECONDS = 1;

    private final Context context;
    private final DdapFrontendClient ddapFrontendClient;
    private final ObjectMapper objectMapper;

    public static class GetAccessException extends Exception {
        GetAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public ResourceTokens getAccessToken(String interfaceId) throws GetAccessException {
        String cliSessionId = UUID.randomUUID().toString(); // Ideally it should check first if same UUID exists
        String baseUrl = String.format("%s/api", context.getUrl());
        String redirectUrl = getRedirectUrl(baseUrl, context.getRealm(), cliSessionId, interfaceId);
        String authorizeUrl = String.format(
            "%s/v1beta/%s/resources/authorize?resource=%s&redirect_uri=%s",
            baseUrl, context.getRealm(), interfaceId, redirectUrl
        );
        URI authorizeStatusUri = URI.create(String.format(
            "%s/v1alpha/realm/%s/cli/%s/authorize/status?resource=%s",
            baseUrl, context.getRealm(), cliSessionId, interfaceId)
        );

        ddapFrontendClient.clearCartToken(context.getRealm(), cliSessionId); // Get rid of old/stale stored tokens first
        displayLinkToAuthorization(interfaceId, authorizeUrl);
        try {
            System.out.printf("Waiting for web authorization to complete for next %d seconds...%n", TIMEOUT_IN_SECONDS);
            ResourceTokens tokens = pollStatus(() -> ddapFrontendClient.authorizeStatus(authorizeStatusUri));
            System.out.println("Authorization successful");

            return tokens;
        } catch (FeignException fe) {
            final String message = parseDdapErrorMessage(objectMapper, fe);
            throw new GetAccessException(format("Could not get access to %s%n%d : %s%n",
                interfaceId,
                fe.status(),
                message), fe);
        }
    }

    private void displayLinkToAuthorization(String interfaceId, String authorizeUrl) {
        System.out.printf("Visit this link in a web browser to authorize for resource [%s] : %s%n", interfaceId, authorizeUrl);
    }

    private static String getRedirectUrl(String ddapBaseUrl, String realm, String cliSessionId, String interfaceId) {
        return String.format("%s/v1alpha/realm/%s/cli/%s/authorize/callback?resource=%s", ddapBaseUrl, realm, cliSessionId, interfaceId);
    }

    private static ResourceTokens pollStatus(Supplier<ResourceTokens> pollingAction) {
        final Instant start = Instant.now();
        do {
            final ResourceTokens tokens = pollingAction.get();
            if (tokens != null) {
                return tokens;
            }
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(INTERVAL_IN_SECONDS));
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while polling for authorize status.");
            }
        } while (start.plusMillis(TimeUnit.SECONDS.toMillis(TIMEOUT_IN_SECONDS)).isAfter(Instant.now()));

        throw new RuntimeException("Exceeded timeout while waiting for authorize.");
    }
}
