package com.stock.strategy.indicator.movingaverage;

import java.time.LocalDate;
import java.util.Objects;

public record MovingAverageIndicator(
        String symbol,
        LocalDate asOfTradingDate,
        SimpleMovingAverage shortMovingAverage,
        SimpleMovingAverage longMovingAverage
) {
    public MovingAverageIndicator {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        Objects.requireNonNull(
                asOfTradingDate,
                "asOfTradingDate must not be null."
        );
        Objects.requireNonNull(
                shortMovingAverage,
                "shortMovingAverage must not be null."
        );
        Objects.requireNonNull(
                longMovingAverage,
                "longMovingAverage must not be null."
        );
        if (!symbol.equals(shortMovingAverage.symbol())
                || !symbol.equals(longMovingAverage.symbol())) {
            throw new IllegalArgumentException(
                    "Moving averages must have the same symbol."
            );
        }
        if (shortMovingAverage.period() >= longMovingAverage.period()) {
            throw new IllegalArgumentException(
                    "Short moving average period must be less than long "
                            + "moving average period."
            );
        }
        if (!shortMovingAverage.toTradingDate()
                .equals(longMovingAverage.toTradingDate())) {
            throw new IllegalArgumentException(
                    "Moving averages must have the same toTradingDate."
            );
        }
        if (!asOfTradingDate.equals(shortMovingAverage.toTradingDate())) {
            throw new IllegalArgumentException(
                    "asOfTradingDate must match moving average "
                            + "toTradingDate."
            );
        }
    }
}
