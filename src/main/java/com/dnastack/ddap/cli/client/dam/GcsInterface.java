package com.dnastack.ddap.cli.client.dam;

import lombok.Data;

import java.util.List;

@Data
public class GcsInterface extends Interface {
    private List<String> uri;
}
