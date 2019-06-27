package com.dnastack.ddap.cli.client.dam;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class Interface {

    @JsonIgnore
    @JsonAnySetter
    private final Map<String, List<String>> bindings = new HashMap<>();

    @JsonAnyGetter
    public Map<String, List<String>> getBindings() {
        return bindings;
    }
}
