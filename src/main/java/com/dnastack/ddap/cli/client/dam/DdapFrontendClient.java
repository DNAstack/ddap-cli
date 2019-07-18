package com.dnastack.ddap.cli.client.dam;

import feign.*;

import java.net.URI;
import java.util.Map;

public interface DdapFrontendClient {

    String API_VERSION = "v1alpha";

    @RequestLine("POST /api/" + API_VERSION + "/{realm}/cli/login")
    Response startCommandLineLogin(@Param("realm") String realm);

    @RequestLine("GET {url}")
    @Headers("Cookie: status_token={token}")
    LoginStatus loginStatus(URI uri, @Param("token") String token);

    @RequestLine("GET {url}/{realm}/resources")
    ResourceResponse getResources(URI uri, @Param("realm") String realm);

    @RequestLine("GET {url}/{realm}/resources/{resourceId}/views/{viewId}/token?ttl={ttl}")
    @Headers("Cookie: dam_token={damToken}")
    ViewAccessTokenResponse getAccessToken(URI uri,
                                           @Param("realm") String realm,
                                           @Param("damToken") String damToken,
                                           @Param("resourceId") String resourceId,
                                           @Param("viewId") String viewId,
                                           @Param("ttl") String ttl);

    @RequestLine("GET /api/" + API_VERSION + "/master/dam")
    Map<String, DamInfo> getDamInfos();

}
