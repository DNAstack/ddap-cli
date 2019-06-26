package com.dnastack.ddap.cli.client.dam;

import lombok.Data;

@Data
public class LoginTokenResponse {
    private String idToken;
    private String accessToken;
}
