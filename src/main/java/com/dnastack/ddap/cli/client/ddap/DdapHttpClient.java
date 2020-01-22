package com.dnastack.ddap.cli.client.ddap;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class DdapHttpClient {

    private static HttpClient buildClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    public HttpCookie loginToDdap(String ddapBaseUri, String username, String password) {
        String form = String.format("username=%s&password=%s", username, password);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ddapBaseUri).resolve("/login"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();

        try {
            HttpResponse<String> response = DdapHttpClient.buildClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
            return response.headers().firstValue("Set-Cookie")
                .map(HttpCookie::parse)
                .map((cookies) -> {
                    // It is safe to assume that there is just one Set-Cookie definition per header line
                    return cookies.get(0);
                })
                .orElseThrow(InvalidCredentialsException::new);
        } catch (IOException | InterruptedException e) {
            throw new DdapClientException(e);
        }
    }

}
