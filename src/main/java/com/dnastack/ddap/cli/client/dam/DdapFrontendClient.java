package com.dnastack.ddap.cli.client.dam;

import feign.Param;
import feign.RequestLine;

public interface DdapFrontendClient {

    String API_VERSION = "v1alpha";

    @RequestLine("GET /api/" + API_VERSION + "/{realm}/identity/login?user_agent=cli")
    TokenResponse commandLineLogin(@Param("realm") String realm);

}
