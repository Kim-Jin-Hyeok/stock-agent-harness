package com.stock.harness.persistence;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;

public record HarnessDecisionSnapshot(
        InvestmentAction action,
        String symbol,
        Long quantity,
        Long expectedPriceKrw,
        Long estimatedOrderAmountKrw,
        String reason
) {
    public static HarnessDecisionSnapshot from(InvestmentDecision decision) {
        return new HarnessDecisionSnapshot(
                decision.action(),
                decision.symbol(),
                decision.quantity(),
                decision.expectedPriceKrw(),
                decision.estimatedOrderAmountKrw(),
                decision.reason()
        );
    }
}
