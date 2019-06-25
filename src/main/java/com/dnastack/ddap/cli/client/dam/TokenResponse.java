package com.dnastack.ddap.cli.client.dam;

import lombok.Data;

@Data
public class TokenResponse {
    private String idToken;
    private String accessToken;
}
