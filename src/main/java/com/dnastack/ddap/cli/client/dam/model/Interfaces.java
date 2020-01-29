package com.dnastack.ddap.cli.client.dam.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Data
public class Interfaces {
    @JsonInclude(NON_NULL)
    @JsonProperty("gcp:gs")
    private GcsInterface gcs;

    @JsonInclude(NON_NULL)
    @JsonProperty("http:gcp:gs")
    private GcsInterface httpGcs;

    @JsonAnySetter
    private final Map<String, Interface> otherInterfaces = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Interface> getOtherInterfaces() {
        return otherInterfaces;
    }
}
