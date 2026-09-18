package com.stock.market.price.provider.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisCurrentPriceResponse(
        @JsonProperty("rt_cd") String resultCode,
        @JsonProperty("msg_cd") String messageCode,
        @JsonProperty("msg1") String message,
        KisCurrentPriceOutput output
) {
    public boolean isSuccessful() {
        return "0".equals(resultCode);
    }
}
