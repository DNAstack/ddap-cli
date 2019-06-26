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

    @RequestLine("GET /dam/" + API_VERSION + "/{realm}/resources")
    ResourceResponse getResources(@Param("realm") String realm);

    @RequestLine("GET /dam/" + API_VERSION + "/{realm}/resources/{resourceId}/views/{viewId}/token?ttl={ttl}")
    @Headers("Cookie: dam_token={damToken}")
    ViewAccessTokenResponse getAccessToken(@Param("realm") String realm,
                                           @Param("damToken") String damToken,
                                           @Param("resourceId") String resourceId,
                                           @Param("viewId") String viewId,
                                           @Param("ttl") String ttl);

}
