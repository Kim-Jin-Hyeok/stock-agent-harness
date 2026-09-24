package com.stock.broker.kis.order.cancellation.inquiry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record KisCancelableOrderResponse(
        @JsonProperty("rt_cd") String resultCode,
        @JsonProperty("msg_cd") String messageCode,
        @JsonProperty("msg1") String message,
        List<KisCancelableOrderOutput> output,
        @JsonProperty("ctx_area_fk100") String contextAreaFk100,
        @JsonProperty("ctx_area_nk100") String contextAreaNk100
) {
    public boolean isSuccessful() {
        return "0".equals(resultCode);
    }
}
