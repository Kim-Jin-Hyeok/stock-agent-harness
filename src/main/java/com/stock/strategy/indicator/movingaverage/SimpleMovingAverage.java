package com.stock.strategy.indicator.movingaverage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record SimpleMovingAverage(
        String symbol,
        int period,
        BigDecimal averagePriceKrw,
        LocalDate fromTradingDate,
        LocalDate toTradingDate
) {
    public SimpleMovingAverage {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive.");
        }
        Objects.requireNonNull(
                averagePriceKrw,
                "averagePriceKrw must not be null."
        );
        if (averagePriceKrw.signum() <= 0) {
            throw new IllegalArgumentException(
                    "averagePriceKrw must be positive."
            );
        }
        Objects.requireNonNull(
                fromTradingDate,
                "fromTradingDate must not be null."
        );
        Objects.requireNonNull(
                toTradingDate,
                "toTradingDate must not be null."
        );
        if (fromTradingDate.isAfter(toTradingDate)) {
            throw new IllegalArgumentException(
                    "fromTradingDate must not be after toTradingDate."
            );
        }
    }
}
