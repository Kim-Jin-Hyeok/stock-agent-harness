package com.stock.broker.account;

public record BrokerAccountPosition(
        String symbol,
        long quantity,
        long averagePriceKrw,
        long evaluationAmountKrw
) {
    public BrokerAccountPosition {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative.");
        }
        if (averagePriceKrw < 0) {
            throw new IllegalArgumentException("averagePriceKrw must not be negative.");
        }
        if (evaluationAmountKrw < 0) {
            throw new IllegalArgumentException("evaluationAmountKrw must not be negative.");
        }
    }
}
