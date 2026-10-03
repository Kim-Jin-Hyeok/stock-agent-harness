package com.stock.strategy.universe.liquidity.selection.result;

import com.stock.strategy.universe.liquidity.DailyTradingValueAverage;

import java.util.Objects;

public record DailyTradingValueSelectionResult(
        DailyTradingValueAverage average,
        int rank,
        DailyTradingValueSelectionStatus status
) {
    public DailyTradingValueSelectionResult {
        Objects.requireNonNull(average, "average must not be null.");
        if (rank <= 0) {
            throw new IllegalArgumentException("rank must be positive.");
        }
        Objects.requireNonNull(status, "status must not be null.");
    }
}
