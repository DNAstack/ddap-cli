package com.dnastack.ddap.cli.login;

import com.dnastack.ddap.cli.client.dam.DamInfo;
import com.dnastack.ddap.cli.client.dam.LoginTokenResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Context {
    private String url;
    private String realm;
    private Map<String, DamInfo> damInfos;
    private LoginTokenResponse tokens;
    private BasicCredentials basicCredentials;
}
