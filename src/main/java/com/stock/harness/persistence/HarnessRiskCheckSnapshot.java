package com.stock.harness.persistence;

import com.stock.agent.InvestmentAction;
import com.stock.risk.RiskCheckResult;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskReasonCode;

public record HarnessRiskCheckSnapshot(
        RiskCheckStatus status,
        InvestmentAction action,
        String symbol,
        Long quantity,
        Long expectedPriceKrw,
        Long estimatedOrderAmountKrw,
        RiskReasonCode reasonCode,
        String reason
) {
    public static HarnessRiskCheckSnapshot from(RiskCheckResult result) {
        return new HarnessRiskCheckSnapshot(
                result.status(),
                result.action(),
                result.symbol(),
                result.quantity(),
                result.expectedPriceKrw(),
                result.estimatedOrderAmountKrw(),
                result.reasonCode(),
                result.reason()
        );
    }
}
