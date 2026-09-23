package com.stock.broker.kis.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisCashOrderResponse(
        @JsonProperty("rt_cd") String resultCode,
        @JsonProperty("msg_cd") String messageCode,
        @JsonProperty("msg1") String message,
        KisCashOrderOutput output
) {
    public boolean isSuccessful() {
        return "0".equals(resultCode);
    }
}
