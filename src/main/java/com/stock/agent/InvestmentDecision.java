package com.stock.agent;

public record InvestmentDecision(
        InvestmentAction action,
        String symbol,
        Long quantity,
        Long expectedPriceKrw,
        String reason
) {
    public Long estimatedOrderAmountKrw() {
        if (InvestmentAction.HOLD.equals(action)
                || quantity == null
                || expectedPriceKrw == null) {
            return 0L;
        }

        return quantity * expectedPriceKrw;
    }
}
