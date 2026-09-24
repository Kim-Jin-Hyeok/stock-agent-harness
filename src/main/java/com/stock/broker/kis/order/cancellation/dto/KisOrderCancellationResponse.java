package com.stock.broker.kis.order.cancellation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisOrderCancellationResponse(
        @JsonProperty("rt_cd") String resultCode,
        @JsonProperty("msg_cd") String messageCode,
        @JsonProperty("msg1") String message,
        KisOrderCancellationOutput output
) {
    public boolean isSuccessful() {
        return "0".equals(resultCode);
    }
}
