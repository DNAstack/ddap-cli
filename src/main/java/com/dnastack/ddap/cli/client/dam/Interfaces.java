package com.dnastack.ddap.cli.client.dam;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class Interfaces {
    @JsonProperty("gcp:gs")
    private GcsInterface gcs;
    @JsonProperty("http:gcp:gs")
    private GcsInterface httpGcs;

    @JsonAnySetter
    private Map<String, Interface> otherInterfaces;

    @JsonAnyGetter
    public Map<String, Interface> getOtherInterfaces() {
        return otherInterfaces;
    }
}
