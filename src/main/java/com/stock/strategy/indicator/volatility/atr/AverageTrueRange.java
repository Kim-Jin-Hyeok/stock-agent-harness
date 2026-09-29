package com.stock.strategy.indicator.volatility.atr;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record AverageTrueRange(
        String symbol,
        int period,
        BigDecimal averageTrueRangeKrw,
        LocalDate fromTradingDate,
        LocalDate toTradingDate
) {
    public AverageTrueRange {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive.");
        }
        Objects.requireNonNull(
                averageTrueRangeKrw,
                "averageTrueRangeKrw must not be null."
        );
        if (averageTrueRangeKrw.signum() < 0) {
            throw new IllegalArgumentException(
                    "averageTrueRangeKrw must not be negative."
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
