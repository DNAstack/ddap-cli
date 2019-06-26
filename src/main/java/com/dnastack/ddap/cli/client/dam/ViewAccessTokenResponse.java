package com.dnastack.ddap.cli.client.dam;

import lombok.Data;

@Data
public class ViewAccessTokenResponse {
    private String account;
    private String token;
    private String ttl;
}
