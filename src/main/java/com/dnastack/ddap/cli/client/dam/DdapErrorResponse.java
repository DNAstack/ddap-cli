package com.dnastack.ddap.cli.client.dam;

import lombok.Data;

@Data
public class DdapErrorResponse {
    private String message;
    private int statusCode;
}
