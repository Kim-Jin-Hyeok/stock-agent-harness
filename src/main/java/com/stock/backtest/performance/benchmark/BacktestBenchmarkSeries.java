package com.stock.backtest.performance.benchmark;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record BacktestBenchmarkSeries(
        String benchmarkId,
        List<BacktestBenchmarkObservation> observations
) {
    public BacktestBenchmarkSeries {
        if (benchmarkId == null || benchmarkId.isBlank()) {
            throw new IllegalArgumentException(
                    "benchmarkId must not be blank."
            );
        }
        observations = List.copyOf(Objects.requireNonNull(
                observations,
                "observations must not be null."
        ));
        if (observations.isEmpty()) {
            throw new IllegalArgumentException(
                    "observations must not be empty."
            );
        }
        validateObservationOrder(observations);
    }

    private static void validateObservationOrder(
            List<BacktestBenchmarkObservation> observations
    ) {
        LocalDate previousDate = null;
        for (BacktestBenchmarkObservation observation : observations) {
            Objects.requireNonNull(
                    observation,
                    "benchmark observation must not be null."
            );
            if (previousDate != null
                    && !observation.observationDate()
                    .isAfter(previousDate)) {
                throw new IllegalArgumentException(
                        "observations must be ordered by unique "
                                + "observationDate."
                );
            }
            previousDate = observation.observationDate();
        }
    }
}
