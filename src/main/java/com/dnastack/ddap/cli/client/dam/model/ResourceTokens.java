package com.dnastack.ddap.cli.client.dam.model;

import com.fasterxml.jackson.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ResourceTokens {

    @JsonIgnore
    private String principalId;
    @JsonIgnore
    private String interfaceId;

    @JsonIgnore
    private String encryptedCredentials;

    @JsonIgnore
    public String getAccessToken(){
        if (credentials != null){
            return credentials.get("access_token");
        }
        return null;
    }

    private Map<String, String> credentials;

}
