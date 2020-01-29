package com.dnastack.ddap.cli.client.dam.model;

import lombok.Data;

import java.util.List;

@Data
public class GcsInterface extends Interface {
    private List<String> uri;
}
