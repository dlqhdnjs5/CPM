package com.bowon.cpm.dart.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.dart")
public record DartProperties(
        String baseUrl,
        String apiKey
) {
}

