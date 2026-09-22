package com.stock.broker.kis.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisAccountBalanceSummaryOutput(
        @JsonProperty("dnca_tot_amt") String depositAmount,
        @JsonProperty("tot_evlu_amt") String totalEvaluationAmount
) {
    public long depositAmountKrw() {
        return KisAccountBalanceNumberParser.parseNonNegativeLong(
                depositAmount,
                "depositAmount"
        );
    }

    public long totalAssetAmountKrw() {
        return KisAccountBalanceNumberParser.parseNonNegativeLong(
                totalEvaluationAmount,
                "totalEvaluationAmount"
        );
    }
}
