package com.stock.risk;

import com.stock.agent.InvestmentAction;

public record RiskCheckResult(
        RiskCheckStatus status,
        InvestmentAction action,
        String symbol,
        Long orderAmountKrw,
        RiskReasonCode reasonCode,
        String reason
) {
}
