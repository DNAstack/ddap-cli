package com.dnastack.ddap.cli.client.dam;

import lombok.Data;

import java.util.Map;

@Data
public class Resource {
    private Map<String, View> views;
    private Map<String, String> ui;
}
