package com.stock.broker.kis.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisTokenRequest(
        @JsonProperty("grant_type") String grantType,
        @JsonProperty("appkey") String appKey,
        @JsonProperty("appsecret") String appSecret
) {
    @Override
    public String toString() {
        return "KisTokenRequest[grantType=<redacted>, appKey=<redacted>, appSecret=<redacted>]";
    }
}
