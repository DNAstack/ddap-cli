package com.dnastack.ddap.cli.login;

import com.dnastack.ddap.cli.client.dam.DdapFrontendClient;
import com.dnastack.ddap.cli.client.dam.LoginStatus;
import com.dnastack.ddap.cli.client.dam.LoginTokenResponse;
import com.dnastack.ddap.cli.client.dam.StartLoginResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Response;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.dnastack.ddap.cli.client.HttpUtil.*;
import static java.lang.String.format;

@AllArgsConstructor
public class LoginCommand {
    private static final int TIMEOUT_IN_SECONDS = 10 * 60;
    private static final int INTERVAL_IN_SECONDS = 1;

    private final ObjectMapper objectMapper;
    private final DdapFrontendClient ddapFrontendClient;
    @Getter
    private final String realm;

    public LoginTokenResponse login() throws LoginException {
        final Response cliLoginCreateResponse = ddapFrontendClient.startCommandLineLogin(realm);
        if (isSuccess(cliLoginCreateResponse.status())
                && hasLocation(cliLoginCreateResponse)) {
            final URI cliLoginStatusUrl = URI.create(cliLoginCreateResponse.headers().get("Location").iterator().next());

            try {
                final StartLoginResponse startLoginResponse = objectMapper.readValue(cliLoginCreateResponse.body().asInputStream(), StartLoginResponse.class);
                final LoginStatus loginStatus = ddapFrontendClient.loginStatus(cliLoginStatusUrl, startLoginResponse.getToken());
                final URI webLoginUrl = loginStatus.getWebLoginUrl();

                System.out.printf("Visit this link in a web browser to login: %s\n", webLoginUrl);

                System.out.printf("Waiting for web login to complete for next %d seconds...\n", TIMEOUT_IN_SECONDS);
                final LoginTokenResponse loginTokenResponse = pollStatus(() -> ddapFrontendClient.loginStatus(cliLoginStatusUrl,
                                                                                                              startLoginResponse.getToken()));
                System.out.println("Login successful");

                return loginTokenResponse;
            } catch (FeignException fe) {
                final String message = parseDdapErrorMessage(objectMapper, fe);
                throw new LoginException(format("Could not poll login status.\n%d : %s\n", fe.status(), message));
            } catch (Exception e) {
                throw new LoginException(format("Client error encountered: %s\n", e.getMessage()), e);
            }
        } else {
            final String message = parseDdapErrorMessage(objectMapper, cliLoginCreateResponse.body());
            throw new LoginException(format("Could not initiate login\n%d : %s\n%s\n",
                                            cliLoginCreateResponse.status(),
                                            cliLoginCreateResponse.reason(),
                                            message));
        }
    }

    private static LoginTokenResponse pollStatus(Supplier<LoginStatus> pollingAction) {
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

    public static class LoginException extends Exception {
        public LoginException(String message) {
            super(message);
        }

        public LoginException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
