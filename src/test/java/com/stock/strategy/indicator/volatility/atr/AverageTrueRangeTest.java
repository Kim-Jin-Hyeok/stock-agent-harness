package com.stock.strategy.indicator.volatility.atr;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AverageTrueRangeTest {
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 9, 2);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 9, 5);

    @Test
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> averageTrueRange(" ", 14, "1000.00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must not be blank.");
        assertThatThrownBy(() -> averageTrueRange("005930", 0, "1000.00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("period must be positive.");
        assertThatThrownBy(() -> averageTrueRange("005930", 14, "-0.01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("averageTrueRangeKrw must not be negative.");
    }

    @Test
    void rejectsReversedTradingDateRange() {
        assertThatThrownBy(() -> new AverageTrueRange(
                "005930",
                14,
                new BigDecimal("1000.00"),
                TO_DATE,
                FROM_DATE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "fromTradingDate must not be after toTradingDate."
                );
    }

    private AverageTrueRange averageTrueRange(
            String symbol,
            int period,
            String averageTrueRangeKrw
    ) {
        return new AverageTrueRange(
                symbol,
                period,
                new BigDecimal(averageTrueRangeKrw),
                FROM_DATE,
                TO_DATE
        );
    }
}
