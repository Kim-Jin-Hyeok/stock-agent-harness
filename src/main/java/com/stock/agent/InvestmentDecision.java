package com.stock.agent;

import com.stock.agent.evidence.movingaverage.MovingAverageDecisionEvidence;

public record InvestmentDecision(
        InvestmentAction action,
        String symbol,
        Long quantity,
        Long expectedPriceKrw,
        String reason,
        MovingAverageDecisionEvidence movingAverageEvidence
) {
    public InvestmentDecision(
            InvestmentAction action,
            String symbol,
            Long quantity,
            Long expectedPriceKrw,
            String reason
    ) {
        this(
                action,
                symbol,
                quantity,
                expectedPriceKrw,
                reason,
                null
        );
    }

    public Long estimatedOrderAmountKrw() {
        if (InvestmentAction.HOLD.equals(action)
                || quantity == null
                || expectedPriceKrw == null) {
            return 0L;
        }

        return quantity * expectedPriceKrw;
    }
}
