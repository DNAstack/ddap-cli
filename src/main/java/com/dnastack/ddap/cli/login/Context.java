package com.dnastack.ddap.cli.login;

import com.dnastack.ddap.cli.client.dam.model.DamInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Context {

    private String url;
    private String realm;
    private Credentials credentials;

    public Context(String url, String realm, Credentials credentials) {
        this.url = url;
        this.realm = realm;
        this.credentials = credentials;
    }

    @JsonIgnore
    private boolean changed = false;

}
