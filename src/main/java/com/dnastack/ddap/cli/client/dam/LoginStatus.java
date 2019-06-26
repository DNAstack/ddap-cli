package com.dnastack.ddap.cli.client.dam;

import lombok.Data;

import java.net.URI;

@Data
public class LoginStatus {
    private LoginTokenResponse tokens;
    private URI webLoginUrl;
}
