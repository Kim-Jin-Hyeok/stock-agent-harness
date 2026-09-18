package com.stock.broker.kis.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisTokenRequest(
        @JsonProperty("grant_type") String grantType,
        @JsonProperty("appkey") String appKey,
        @JsonProperty("appsecret") String appSecret
) {
}
