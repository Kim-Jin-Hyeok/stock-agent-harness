package com.stock.market.index.history;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record MarketIndexDailyObservation(
        LocalDate observationDate,
        BigDecimal closeValue
) {
    public MarketIndexDailyObservation {
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
