package com.stock.backtest.portfolio;

public record BacktestPosition(
        String symbol,
        long quantity,
        long averageExecutionPriceKrw
) {
    public BacktestPosition {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive.");
        }
        if (averageExecutionPriceKrw <= 0) {
            throw new IllegalArgumentException(
                    "averageExecutionPriceKrw must be positive."
            );
        }
    }
}
