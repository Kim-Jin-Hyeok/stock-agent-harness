package com.stock.backtest.performance.benchmark;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record BacktestBenchmarkObservation(
        LocalDate observationDate,
        BigDecimal closeValue
) {
    public BacktestBenchmarkObservation {
        Objects.requireNonNull(
                observationDate,
                "observationDate must not be null."
        );
        Objects.requireNonNull(
                closeValue,
                "closeValue must not be null."
        );
        if (closeValue.signum() <= 0) {
            throw new IllegalArgumentException(
                    "closeValue must be positive."
            );
        }
    }
}
