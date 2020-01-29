package com.dnastack.ddap.cli.client.dam.model;

import lombok.Data;

import java.util.Map;

@Data
public class View {
    private Map<String, String> ui;
    private Interfaces interfaces;
    private Map<String, Object> roles;
    private String defaultRole;
}
