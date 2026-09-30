package com.stock.backtest.performance.benchmark;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BacktestBenchmarkSeriesTest {
    private static final LocalDate FIRST_DATE =
            LocalDate.of(2026, 9, 1);

    @Test
    void copiesOrderedObservations() {
        BacktestBenchmarkObservation first = observation(0, "100");
        BacktestBenchmarkObservation second = observation(1, "110");
        List<BacktestBenchmarkObservation> mutableObservations =
                new ArrayList<>(List.of(first, second));

        BacktestBenchmarkSeries series = new BacktestBenchmarkSeries(
                "KOSPI",
                mutableObservations
        );
        mutableObservations.clear();

        assertThat(series.benchmarkId()).isEqualTo("KOSPI");
        assertThat(series.observations()).containsExactly(first, second);
        assertThatThrownBy(() -> series.observations().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateObservationDate() {
        assertThatThrownBy(() -> new BacktestBenchmarkSeries(
                "KOSPI",
                List.of(
                        observation(0, "100"),
                        observation(0, "110")
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "observations must be ordered by unique "
                                + "observationDate."
                );
    }

    @Test
    void rejectsObservationsOutsideDateOrder() {
        assertThatThrownBy(() -> new BacktestBenchmarkSeries(
                "KOSPI",
                List.of(
                        observation(1, "110"),
                        observation(0, "100")
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "observations must be ordered by unique "
                                + "observationDate."
                );
    }

    @Test
    void rejectsEmptyObservations() {
        assertThatThrownBy(() -> new BacktestBenchmarkSeries(
                "KOSPI",
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("observations must not be empty.");
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
