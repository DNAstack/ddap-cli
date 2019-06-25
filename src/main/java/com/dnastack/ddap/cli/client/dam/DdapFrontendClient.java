package com.dnastack.ddap.cli.client.dam;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.Response;

import java.net.URI;

public interface DdapFrontendClient {

    String API_VERSION = "v1alpha";

    @RequestLine("POST /api/" + API_VERSION + "/{realm}/cli/login")
    Response startCommandLineLogin(@Param("realm") String realm);

    @RequestLine("GET {url}")
    @Headers("Authorization: {auth}")
    LoginStatus loginStatus(URI uri, @Param("auth") String auth);

}
