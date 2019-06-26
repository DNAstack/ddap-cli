package com.dnastack.ddap.cli.login;

import com.dnastack.ddap.cli.client.dam.LoginTokenResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Context {
    private String url;
    private String realm;
    private LoginTokenResponse tokens;
    private BasicCredentials basicCredentials;
}
