package com.dnastack.ddap.cli.client.ddap;

import com.dnastack.ddap.cli.login.Credentials;
import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class DdapHttpClient {

    private static HttpClient buildClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    public List<HttpCookie> loginToDdap(String ddapBaseUri, Credentials credentials) {
        HttpRequest request;

        if (credentials.getSessionId() != null && credentials.getSessionDecryptionKey() != null) {
            return loginWithOldSessionId(ddapBaseUri, credentials);
        }

        if (credentials.getUsername() != null && credentials.getPassword() != null) {
            String form = String
                .format("username=%s&password=%s", credentials.getPassword(), credentials.getPassword());
            request = HttpRequest.newBuilder()
                .uri(URI.create(ddapBaseUri).resolve("/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        } else {
            request = HttpRequest.newBuilder()
                .uri(URI.create(ddapBaseUri))
                .GET()
                .build();
        }

        try {
            HttpResponse<String> response = DdapHttpClient.buildClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new DdapClientException("Could not login to DDAP");
            }
            return extractCookies(response);
        } catch (IOException | InterruptedException e) {
            throw new DdapClientException(e);
        }
    }

    private List<HttpCookie> loginWithOldSessionId(String ddapBaseUri, Credentials credentials) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ddapBaseUri))
            .header("Cookie", String
                .format("SESSION=%s;SESSION_DECRYPTION_KEY=%s;", credentials.getSessionId(), credentials
                    .getSessionDecryptionKey()))
            .GET()
            .build();

        try {
            HttpResponse<String> response = DdapHttpClient.buildClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 302 && response.headers().firstValue("location")
                .map(l -> l.endsWith("/login") || response.statusCode() == 401)
                .orElse(false)) {
                return loginToDdap(ddapBaseUri, new Credentials(credentials.getUsername(), credentials
                    .getPassword(), null, null));
            } else {
                return extractCookies(response);
            }
        } catch (IOException | InterruptedException e) {
            throw new DdapClientException(e);
        }
    }


    private List<HttpCookie> extractCookies(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie")
            .stream()
            .map(HttpCookie::parse)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }
}
