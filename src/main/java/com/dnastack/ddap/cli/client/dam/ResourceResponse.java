package com.dnastack.ddap.cli.client.dam;

import lombok.Data;

import java.util.Map;

@Data
public class ResourceResponse {
    private Map<String, Resource> resources;
}
