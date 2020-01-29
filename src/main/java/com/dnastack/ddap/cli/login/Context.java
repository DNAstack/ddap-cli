package com.dnastack.ddap.cli.login;

import com.dnastack.ddap.cli.client.dam.model.DamInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Context {

    private String url;
    private String realm;
    private Map<String, DamInfo> damInfos;
    private Credentials credentials;

}
