package com.dnastack.ddap.cli.client.dam.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ResourceTokens {

    private Map<String, Descriptor> resources;
    private Map<String, ResourceToken> access;

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Descriptor {
        private Map<String, Interface> interfaces;
        private String access;
        private List<String> permissions;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class ResourceToken {
        private String account;
        @JsonProperty("access_token")
        private String accessToken;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Interface {
        private List<String> uri;
    }

}
