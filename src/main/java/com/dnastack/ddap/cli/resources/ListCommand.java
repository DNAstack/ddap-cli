package com.dnastack.ddap.cli.resources;

import com.dnastack.ddap.cli.client.dam.DdapFrontendClient;
import com.dnastack.ddap.cli.client.dam.ResourceResponse;
import com.dnastack.ddap.cli.login.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.AllArgsConstructor;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

import static com.dnastack.ddap.cli.client.HttpUtil.parseDdapErrorMessage;
import static java.lang.String.format;

@AllArgsConstructor
public class ListCommand {
    private final Context context;
    private final DdapFrontendClient ddapFrontendClient;
    private final ObjectMapper objectMapper;

    public static class ListException extends Exception {
        ListException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public Map<String, ResourceResponse> listResources() throws ListException {
        try {
            return context.getDamInfos()
                          .values()
                          .stream()
                          .map(damInfo -> Map.entry(damInfo.getId(), ddapFrontendClient.getResources(URI.create(damInfo.getUrl()), context.getRealm())))
                          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (FeignException fe) {
            final String message = parseDdapErrorMessage(objectMapper, fe);
            throw new ListException(format("Could not list resources%n%d : %s%n", fe.status(), message), fe);
        }
    }
}
