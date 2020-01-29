package com.dnastack.ddap.cli.client.dam;

import com.dnastack.ddap.cli.client.dam.model.DamInfo;
import com.dnastack.ddap.cli.client.dam.model.ResourceResponse;
import com.dnastack.ddap.cli.client.dam.model.ResourceTokens;
import feign.Param;
import feign.RequestLine;

import java.net.URI;
import java.util.Map;

public interface DdapFrontendClient {

    String API_VERSION = "v1alpha";

    @RequestLine("GET {url}")
    ResourceTokens authorizeStatus(URI uri);

    @RequestLine("POST /api/" + API_VERSION + "/realm/{realm}/cli/{cliSessionId}/authorize/clear")
    void clearCartToken(@Param("realm") String realm, @Param("cliSessionId") String cliSessionId);

    @RequestLine("GET {url}/{realm}/resources")
    ResourceResponse getResources(URI uri, @Param("realm") String realm);

    @RequestLine("GET /api/" + API_VERSION + "/realm/master/dam")
    Map<String, DamInfo> getDamInfos();

}
