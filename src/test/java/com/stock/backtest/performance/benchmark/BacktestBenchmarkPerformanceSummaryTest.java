package com.stock.backtest.performance.benchmark;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BacktestBenchmarkPerformanceSummaryTest {
    private static final LocalDate FROM_DATE =
            LocalDate.of(2026, 9, 1);
    private static final LocalDate TO_DATE =
            LocalDate.of(2026, 9, 4);

    @Test
    void createsValidSummary() {
        BacktestBenchmarkPerformanceSummary summary =
                new BacktestBenchmarkPerformanceSummary(
                        "KOSPI",
                        FROM_DATE,
                        TO_DATE,
                        new BigDecimal("100"),
                        new BigDecimal("130"),
                        new BigDecimal("0.3"),
                        new BigDecimal("0.25"),
                        FROM_DATE.plusDays(1),
                        FROM_DATE.plusDays(2),
                        4
                );

        assertThat(summary.benchmarkId()).isEqualTo("KOSPI");
        assertThat(summary.totalReturnRate())
                .isEqualByComparingTo(new BigDecimal("0.3"));
        assertThat(summary.maxDrawdownRate())
                .isEqualByComparingTo(new BigDecimal("0.25"));
        assertThat(summary.maxDrawdownPeakDate())
                .isEqualTo(FROM_DATE.plusDays(1));
        assertThat(summary.maxDrawdownTroughDate())
                .isEqualTo(FROM_DATE.plusDays(2));
        assertThat(summary.observationCount()).isEqualTo(4);
    }

    @Test
    void rejectsTotalReturnThatDoesNotMatchBoundaryValues() {
        assertThatThrownBy(() -> new BacktestBenchmarkPerformanceSummary(
                "KOSPI",
                FROM_DATE,
                TO_DATE,
                new BigDecimal("100"),
                new BigDecimal("130"),
                new BigDecimal("0.2"),
                BigDecimal.ZERO,
                null,
                null,
                4
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "totalReturnRate must match startValue and endValue."
                );
    }

    @Test
    void rejectsDatesForZeroDrawdown() {
        assertThatThrownBy(() -> new BacktestBenchmarkPerformanceSummary(
                "KOSPI",
                FROM_DATE,
                TO_DATE,
                new BigDecimal("100"),
                new BigDecimal("130"),
                new BigDecimal("0.3"),
                BigDecimal.ZERO,
                FROM_DATE,
                TO_DATE,
                4
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Zero max drawdown must not contain dates.");
    }

    @Test
    void rejectsDrawdownDatesOutsideSummaryRange() {
        assertThatThrownBy(() -> new BacktestBenchmarkPerformanceSummary(
                "KOSPI",
                FROM_DATE,
                TO_DATE,
                new BigDecimal("100"),
                new BigDecimal("130"),
                new BigDecimal("0.3"),
                new BigDecimal("0.1"),
                FROM_DATE.minusDays(1),
                FROM_DATE.plusDays(1),
                4
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Max drawdown dates must belong to the summary "
                                + "range."
                );
    }
}
