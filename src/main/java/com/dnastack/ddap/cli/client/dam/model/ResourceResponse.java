package com.dnastack.ddap.cli.client.dam.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ResourceResponse {
    private List<Map<String, Object>> data;
}
