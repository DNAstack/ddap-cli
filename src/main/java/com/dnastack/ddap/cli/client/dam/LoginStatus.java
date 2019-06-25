package com.dnastack.ddap.cli.client.dam;

import lombok.Data;

import java.net.URI;

@Data
public class LoginStatus {
    private TokenResponse tokens;
    private URI webLoginUrl;
}
