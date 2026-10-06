package com.stock.broker.kis.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresInSeconds,
        @JsonProperty("access_token_token_expired") String expiresAt
) {
    @Override
    public String toString() {
        return "KisTokenResponse[accessToken=<redacted>, tokenType=<redacted>, expiresInSeconds="
                + expiresInSeconds + ", expiresAt=<redacted>]";
    }
}
