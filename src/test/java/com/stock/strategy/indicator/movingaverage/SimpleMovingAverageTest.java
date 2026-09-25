package com.stock.strategy.indicator.movingaverage;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleMovingAverageTest {
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 9, 5);

    @Test
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> movingAverage(" ", 5, "70000.00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must not be blank.");
        assertThatThrownBy(() -> movingAverage("005930", 0, "70000.00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("period must be positive.");
        assertThatThrownBy(() -> movingAverage("005930", 5, "0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("averagePriceKrw must be positive.");
    }

    @Test
    void rejectsReversedTradingDateRange() {
        assertThatThrownBy(() -> new SimpleMovingAverage(
                "005930",
                5,
                new BigDecimal("70000.00"),
                TO_DATE,
                FROM_DATE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "fromTradingDate must not be after toTradingDate."
                );
    }

    private SimpleMovingAverage movingAverage(
            String symbol,
            int period,
            String averagePriceKrw
    ) {
        return new SimpleMovingAverage(
                symbol,
                period,
                new BigDecimal(averagePriceKrw),
                FROM_DATE,
                TO_DATE
        );
    }
}
