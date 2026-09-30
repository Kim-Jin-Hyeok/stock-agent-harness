package com.stock.backtest.performance.benchmark;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BacktestBenchmarkPerformanceCalculatorTest {
    private static final LocalDate FIRST_DATE =
            LocalDate.of(2026, 9, 1);

    private final BacktestBenchmarkPerformanceCalculator calculator =
            new BacktestBenchmarkPerformanceCalculator();

    @Test
    void calculatesReturnAndMaximumDrawdownWithinExactRange() {
        BacktestBenchmarkSeries series = series(
                observation(-1, "200"),
                observation(0, "100"),
                observation(1, "120"),
                observation(2, "90"),
                observation(3, "130"),
                observation(4, "50")
        );

        BacktestBenchmarkPerformanceSummary summary =
                calculator.calculate(
                        series,
                        FIRST_DATE,
                        FIRST_DATE.plusDays(3)
                );

        assertThat(summary.benchmarkId()).isEqualTo("KOSPI");
        assertThat(summary.fromDate()).isEqualTo(FIRST_DATE);
        assertThat(summary.toDate()).isEqualTo(FIRST_DATE.plusDays(3));
        assertThat(summary.startValue())
                .isEqualByComparingTo(new BigDecimal("100"));
        assertThat(summary.endValue())
                .isEqualByComparingTo(new BigDecimal("130"));
        assertThat(summary.totalReturnRate())
                .isEqualByComparingTo(new BigDecimal("0.3"));
        assertThat(summary.maxDrawdownRate())
                .isEqualByComparingTo(new BigDecimal("0.25"));
        assertThat(summary.maxDrawdownPeakDate())
                .isEqualTo(FIRST_DATE.plusDays(1));
        assertThat(summary.maxDrawdownTroughDate())
                .isEqualTo(FIRST_DATE.plusDays(2));
        assertThat(summary.observationCount()).isEqualTo(4);
    }

    @Test
    void returnsZeroDrawdownForMonotonicGrowth() {
        BacktestBenchmarkSeries series = series(
                observation(0, "100"),
                observation(1, "110"),
                observation(2, "120")
        );

        BacktestBenchmarkPerformanceSummary summary =
                calculator.calculate(
                        series,
                        FIRST_DATE,
                        FIRST_DATE.plusDays(2)
                );

        assertThat(summary.maxDrawdownRate())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.maxDrawdownPeakDate()).isNull();
        assertThat(summary.maxDrawdownTroughDate()).isNull();
    }

    @Test
    void calculatesSingleDateRange() {
        BacktestBenchmarkSeries series = series(
                observation(0, "100")
        );

        BacktestBenchmarkPerformanceSummary summary =
                calculator.calculate(series, FIRST_DATE, FIRST_DATE);

        assertThat(summary.totalReturnRate())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.maxDrawdownRate())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.observationCount()).isEqualTo(1);
    }

    @Test
    void rejectsMissingFromDateObservation() {
        BacktestBenchmarkSeries series = series(
                observation(1, "110"),
                observation(2, "120")
        );

        assertThatThrownBy(() -> calculator.calculate(
                series,
                FIRST_DATE,
                FIRST_DATE.plusDays(2)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Benchmark observation not found for fromDate: "
                                + FIRST_DATE
                );
    }

    @Test
    void rejectsMissingToDateObservation() {
        BacktestBenchmarkSeries series = series(
                observation(0, "100"),
                observation(1, "110")
        );
        LocalDate missingToDate = FIRST_DATE.plusDays(2);

        assertThatThrownBy(() -> calculator.calculate(
                series,
                FIRST_DATE,
                missingToDate
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Benchmark observation not found for toDate: "
                                + missingToDate
                );
    }

    @Test
    void rejectsReversedDateRange() {
        BacktestBenchmarkSeries series = series(
                observation(0, "100"),
                observation(1, "110")
        );

        assertThatThrownBy(() -> calculator.calculate(
                series,
                FIRST_DATE.plusDays(1),
                FIRST_DATE
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fromDate must not be after toDate.");
    }

    private BacktestBenchmarkSeries series(
            BacktestBenchmarkObservation... observations
    ) {
        return new BacktestBenchmarkSeries(
                "KOSPI",
                List.of(observations)
        );
    }

    private BacktestBenchmarkObservation observation(
            int dayOffset,
            String closeValue
    ) {
        return new BacktestBenchmarkObservation(
                FIRST_DATE.plusDays(dayOffset),
                new BigDecimal(closeValue)
        );
    }
}
