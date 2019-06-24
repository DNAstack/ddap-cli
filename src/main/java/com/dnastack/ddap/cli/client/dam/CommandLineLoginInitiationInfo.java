package com.dnastack.ddap.cli.client.dam;

import lombok.Data;

@Data
public class CommandLineLoginInitiationInfo {
    private String browserLoginUrl;
    private String tokenResponseUrl;
    private String responseBearerToken;
}
