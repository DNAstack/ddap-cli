package com.dnastack.ddap.cli.client.dam.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ResourceTokens {

    private Map<String, Descriptor> resources;
    private Map<String, ResourceAccess> access;

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
    public static class ResourceAccess {
        private Credentials credentials;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Credentials {
        private String account;
        @JsonProperty("access_token")
        private String accessToken;
        @JsonAnySetter
        private Map<String, String> unknown;

        @JsonAnyGetter
        public Map<String, String> getUnknown() {
            return unknown;
        }
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Interface {
        private List<Item> items;
    }


    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Item {
        private String uri;
    }

}
