package com.dnastack.ddap.cli;

import com.dnastack.ddap.cli.client.dam.DdapErrorResponse;
import com.dnastack.ddap.cli.client.dam.DdapFrontendClient;
import com.dnastack.ddap.cli.client.dam.LoginStatus;
import com.dnastack.ddap.cli.client.dam.TokenResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.FeignException;
import feign.Response;
import feign.jackson.JacksonDecoder;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static java.util.concurrent.CompletableFuture.supplyAsync;

public class CommandLineClient {
    private static final int TIMEOUT_IN_SECONDS = 10 * 60;
    private static final int INTERVAL_IN_SECONDS = 1;

    public static void main(String[] args) {
        // TODO: accept as command-line args
        final String ddapRootUrl = "http://localhost:8085";
        final String basicUsername = "dev";
        final String basicPassword = "dev";
        final String realm = "dnastack";

        final String encodedCredentials = Base64.getEncoder()
                                                .encodeToString((basicUsername + ":" + basicPassword).getBytes());

        final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                                                                       false);
        final DdapFrontendClient ddapFrontendClient = Feign.builder()
                                                           .decoder(new JacksonDecoder(objectMapper))
                                                           .requestInterceptor(template -> {
                                                               template.header("Authorization", "Basic " + encodedCredentials);
                                                           })
                                                           .target(DdapFrontendClient.class, ddapRootUrl);

        final Response cliLoginCreateResponse = ddapFrontendClient.startCommandLineLogin(realm);
        if (isSuccess(cliLoginCreateResponse.status())
                && hasLocation(cliLoginCreateResponse)
                && hasAuthorization(cliLoginCreateResponse)) {
            final URI cliLoginStatusUrl = URI.create(cliLoginCreateResponse.headers().get("Location").iterator().next());
            final String loginAuthHeader = cliLoginCreateResponse.headers().get("Authorization").iterator().next();

            try {
                final LoginStatus loginStatus = ddapFrontendClient.loginStatus(cliLoginStatusUrl, loginAuthHeader);
                final URI webLoginUrl = loginStatus.getWebLoginUrl();

                System.out.printf("Visit this link in a web browser to login: %s\n", webLoginUrl);

                final ExecutorService executorService = Executors.newSingleThreadExecutor();
                final CompletableFuture<TokenResponse> tokenFuture =
                        supplyAsync(() -> pollStatus(() -> ddapFrontendClient.loginStatus(cliLoginStatusUrl,
                                                                                          loginAuthHeader)),
                                    executorService);

                System.out.printf("Waiting for web login to complete for next %d seconds...\n", TIMEOUT_IN_SECONDS);
                final TokenResponse tokenResponse = tokenFuture.get();
                System.out.printf("Tokens acquired\n\tAccess Token: %s\n\tId Token: %s\n",
                                  tokenResponse.getAccessToken(),
                                  tokenResponse.getIdToken());
                System.exit(0);
            } catch (FeignException fe) {
                final String message = parseDdapErrorMessage(objectMapper, fe);
                System.err.println("Could not poll login status.");
                System.err.printf("%d : %s\n", fe.status(), message);
                System.exit(1);
            } catch (Exception e) {
                System.err.printf("Client error encountered: %s\n", e.getMessage());
                System.exit(1);
            }
        } else {
            System.err.println("Could not initiate login.");
            System.err.printf("%d : %s\n%s\n",
                              cliLoginCreateResponse.status(),
                              cliLoginCreateResponse.reason(),
                              cliLoginCreateResponse.body());
            System.exit(1);
        }
    }

    private static TokenResponse pollStatus(Supplier<LoginStatus> pollingAction) {
        final Instant start = Instant.now();
        do {
            final LoginStatus loginStatus = pollingAction.get();
            if (loginStatus.getTokens() != null) {
                return loginStatus.getTokens();
            }
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(INTERVAL_IN_SECONDS));
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while polling for login status.");
            }
        } while (start.plusMillis(TimeUnit.SECONDS.toMillis(TIMEOUT_IN_SECONDS)).isAfter(Instant.now()));

        throw new RuntimeException("Exceeded timeout while waiting for login.");
    }

    public static String parseDdapErrorMessage(ObjectMapper objectMapper, FeignException fe) {
        try {
            return objectMapper.readValue(fe.content(), DdapErrorResponse.class).getMessage();
        } catch (IOException e) {
            return fe.contentUTF8();
        }
    }

    private static boolean hasAuthorization(Response response) {
        return !response.headers().getOrDefault("Authorization", Collections.emptyList()).isEmpty();
    }

    private static boolean hasLocation(Response response) {
        return !response.headers().getOrDefault("Location", Collections.emptyList()).isEmpty();
    }

    private static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }
}
