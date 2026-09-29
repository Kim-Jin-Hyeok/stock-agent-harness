package com.stock.backtest.execution.fill.daily;

import com.stock.backtest.execution.fill.BacktestFillType;
import com.stock.trade.cost.model.TradeCostCalculation;

import java.time.LocalDate;
import java.util.Objects;

public record DailyOpenFillApproximation(
        BacktestFillType fillType,
        String symbol,
        LocalDate signalDate,
        LocalDate fillDate,
        TradeCostCalculation tradeCostCalculation
) {
    public DailyOpenFillApproximation {
        Objects.requireNonNull(fillType, "fillType must not be null.");
        if (fillType != BacktestFillType.DAILY_OPEN_FILL_APPROXIMATION) {
            throw new IllegalArgumentException(
                    "fillType must be DAILY_OPEN_FILL_APPROXIMATION."
            );
        }
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        Objects.requireNonNull(
                signalDate,
                "signalDate must not be null."
        );
        Objects.requireNonNull(fillDate, "fillDate must not be null.");
        Objects.requireNonNull(
                tradeCostCalculation,
                "tradeCostCalculation must not be null."
        );
        if (!fillDate.isAfter(signalDate)) {
            throw new IllegalArgumentException(
                    "fillDate must be after signalDate."
            );
        }
    }
}
