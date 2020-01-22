package com.dnastack.ddap.cli.client.dam;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.Logger;
import feign.jackson.JacksonDecoder;
import feign.okhttp.OkHttpClient;

public class FeignClientBuilder {

    public static Feign.Builder getBuilder(ObjectMapper objectMapper, boolean debugLogging) {
        return Feign.builder()
            .client(new OkHttpClient())
            .decoder(new JacksonDecoder(objectMapper))
            .logLevel(debugLogging ? Logger.Level.FULL : Logger.Level.NONE)
            .logger(new Logger() {
                @Override
                protected void log(String configKey, String format, Object... args) {
                    System.out.printf(configKey + " " + format + "%n", args);
                }
            });
    }

}
