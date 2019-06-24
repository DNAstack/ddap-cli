package com.dnastack.ddap.cli;

import com.dnastack.ddap.cli.client.dam.DdapFrontendClient;
import com.dnastack.ddap.cli.client.dam.TokenResponse;
import feign.Feign;
import feign.jackson.JacksonDecoder;

import java.util.Base64;

public class CommandLineClient {
    public static void main(String[] args) {
        final String ddapRootUrl = "http://localhost:8085";
        final String basicUsername = "dev";
        final String basicPassword = "dev";
        final String realm = "dnastack";

        final String encodedCredentials = Base64.getEncoder()
                                                .encodeToString((basicUsername + ":" + basicPassword).getBytes());

        final DdapFrontendClient ddapFrontendClient = Feign.builder()
                                                           .decoder(new JacksonDecoder())
                                                           .requestInterceptor(template -> {
                                                               template.header("Authorization", "Basic " + encodedCredentials);
                                                           })
                                                           .target(DdapFrontendClient.class, ddapRootUrl);

        final TokenResponse tokenResponse = ddapFrontendClient.commandLineLogin(realm);
        System.out.println(tokenResponse);
    }
}
