package com.stock.backtest.performance.benchmark;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BacktestBenchmarkObservationTest {
    private static final LocalDate OBSERVATION_DATE =
            LocalDate.of(2026, 9, 1);

    @Test
    void createsObservationWithDecimalIndexValue() {
        BacktestBenchmarkObservation observation =
                new BacktestBenchmarkObservation(
                        OBSERVATION_DATE,
                        new BigDecimal("3421.37")
                );

        assertThat(observation.observationDate())
                .isEqualTo(OBSERVATION_DATE);
        assertThat(observation.closeValue())
                .isEqualByComparingTo(new BigDecimal("3421.37"));
    }

    @Test
    void rejectsNonPositiveCloseValue() {
        assertThatThrownBy(() -> new BacktestBenchmarkObservation(
                OBSERVATION_DATE,
                BigDecimal.ZERO
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("closeValue must be positive.");
    }

    @Test
    void rejectsNullObservationDate() {
        assertThatNullPointerException()
                .isThrownBy(() -> new BacktestBenchmarkObservation(
                        null,
                        BigDecimal.ONE
                ))
                .withMessage("observationDate must not be null.");
    }
}
