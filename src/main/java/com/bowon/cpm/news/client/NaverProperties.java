package com.bowon.cpm.news.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.naver")
public record NaverProperties(
        String baseUrl,
        String clientId,
        String clientSecret
) {
}

