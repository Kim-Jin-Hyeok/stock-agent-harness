package com.stock.broker.kis.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stock.broker.account.BrokerAccountPosition;

public record KisAccountBalancePositionOutput(
        @JsonProperty("pdno") String symbol,
        @JsonProperty("hldg_qty") String holdingQuantity,
        @JsonProperty("pchs_avg_pric") String averagePurchasePrice,
        @JsonProperty("evlu_amt") String evaluationAmount
) {
    public BrokerAccountPosition toPosition() {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }

        return new BrokerAccountPosition(
                symbol,
                KisAccountBalanceNumberParser.parseNonNegativeLong(
                        holdingQuantity,
                        "holdingQuantity"
                ),
                KisAccountBalanceNumberParser.parseNonNegativeLong(
                        averagePurchasePrice,
                        "averagePurchasePrice"
                ),
                KisAccountBalanceNumberParser.parseNonNegativeLong(
                        evaluationAmount,
                        "evaluationAmount"
                )
        );
    }
}
