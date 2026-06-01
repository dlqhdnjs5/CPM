package com.bowon.cpm.broker.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisTokenResponse(
        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("expires_in")
        Long expiresIn,

        @JsonProperty("access_token_token_expired")
        String accessTokenExpired
) {
}

