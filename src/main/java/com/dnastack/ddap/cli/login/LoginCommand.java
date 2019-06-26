package com.dnastack.ddap.cli.login;

import com.dnastack.ddap.cli.client.dam.DdapFrontendClient;
import com.dnastack.ddap.cli.client.dam.LoginStatus;
import com.dnastack.ddap.cli.client.dam.LoginTokenResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Response;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.dnastack.ddap.cli.HttpUtil.*;
import static java.lang.String.format;

@AllArgsConstructor
public class LoginCommand {
    private static final int TIMEOUT_IN_SECONDS = 10 * 60;
    private static final int INTERVAL_IN_SECONDS = 1;

    private final ObjectMapper objectMapper;
    private final DdapFrontendClient ddapFrontendClient;
    @Getter
    private final String realm;

    public static Collection<Option> getOptions() {
        return List.of(Option.builder("r")
                             .longOpt("realm")
                             .desc("DDAP realm.")
                             .required(false)
                             .hasArg()
                             .type(String.class)
                             .build());
    }

    public static LoginCommand create(ObjectMapper objectMapper, DdapFrontendClient ddapFrontendClient, CommandLine commandLine) {
        final String realm = commandLine.getOptionValue("r", "dnastack");
        return new LoginCommand(objectMapper, ddapFrontendClient, realm);
    }

    public LoginTokenResponse login() throws LoginException {
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

                System.out.printf("Waiting for web login to complete for next %d seconds...\n", TIMEOUT_IN_SECONDS);
                final LoginTokenResponse loginTokenResponse = pollStatus(() -> ddapFrontendClient.loginStatus(cliLoginStatusUrl,
                                                                                                              loginAuthHeader));
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
