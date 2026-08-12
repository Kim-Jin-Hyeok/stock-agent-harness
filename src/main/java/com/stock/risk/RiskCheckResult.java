package com.stock.risk;

import com.stock.agent.InvestmentAction;

public record RiskCheckResult(
        RiskCheckStatus status,
        InvestmentAction action,
        String symbol,
        Long quantity,
        Long expectedPriceKrw,
        Long estimatedOrderAmountKrw,
        RiskReasonCode reasonCode,
        String reason
) {
}
