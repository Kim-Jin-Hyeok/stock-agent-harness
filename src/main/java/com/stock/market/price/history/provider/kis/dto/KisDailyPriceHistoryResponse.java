package com.stock.market.price.history.provider.kis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KisDailyPriceHistoryResponse(
        @JsonProperty("rt_cd") String resultCode,
        @JsonProperty("msg_cd") String messageCode,
        @JsonProperty("msg1") String message,
        @JsonProperty("output2") List<KisDailyPriceBarOutput> output
) {
    public boolean isSuccessful() {
        return "0".equals(resultCode);
    }
}
