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

    public ResourceTokens getAccessToken(DamInfo damInfo, String resourceId, String viewId, String roleId) throws GetAccessException {
        String cliSessionId = UUID.randomUUID().toString(); // Ideally it should check first if same UUID exists
        String damIdResourcePath = String.format("%s;%s/views/%s/roles/%s", damInfo.getId(), resourceId, viewId, roleId);
        String baseUrl = String.format("%s/api/v1alpha", context.getUrl());
        String redirectUrl = getRedirectUrl(baseUrl, context.getRealm(), cliSessionId, damIdResourcePath);
        String authorizeUrl = String.format(
            "%s/realm/%s/resources/authorize?resource=%s;%s/views/%s/roles/%s&redirectUri=%s",
            baseUrl, context.getRealm(), damInfo.getId(), resourceId, viewId, roleId, redirectUrl
        );
        URI authorizeStatusUri = URI.create(String.format(
            "%s/realm/%s/cli/%s/authorize/status?resource=%s",
            baseUrl, context.getRealm(), cliSessionId, damIdResourcePath)
        );

        ddapFrontendClient.clearCartToken(context.getRealm(), cliSessionId); // Get rid of old/stale stored tokens first
        displayLinkToAuthorization(resourceId, viewId, roleId, authorizeUrl);
        try {
            System.out.printf("Waiting for web authorization to complete for next %d seconds...%n", TIMEOUT_IN_SECONDS);
            ResourceTokens tokens = pollStatus(() -> ddapFrontendClient.authorizeStatus(authorizeStatusUri));
            System.out.println("Authorization successful");

            return tokens;
        } catch (FeignException fe) {
            final String message = parseDdapErrorMessage(objectMapper, fe);
            throw new GetAccessException(format("Could not get access to %s/%s%n%d : %s%n",
                resourceId,
                viewId,
                fe.status(),
                message), fe);
        }
    }

    private void displayLinkToAuthorization(String resourceId, String viewId, String roleId, String authorizeUrl) {
        System.out.printf("Visit this link in a web browser to authorize for resource [%s/%s/%s] : %s%n", resourceId, viewId, roleId, authorizeUrl);
    }

    private static String getRedirectUrl(String ddapBaseUrl, String realm, String cliSessionId, String damIdResourcePath) {
        return String.format("%s/realm/%s/cli/%s/authorize/callback?resource=%s", ddapBaseUrl, realm, cliSessionId, damIdResourcePath);
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
