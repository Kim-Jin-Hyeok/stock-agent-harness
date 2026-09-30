package com.stock.market.index.history;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record MarketIndexDailyHistory(
        String benchmarkId,
        List<MarketIndexDailyObservation> observations
) {
    public MarketIndexDailyHistory {
        if (benchmarkId == null || benchmarkId.isBlank()) {
            throw new IllegalArgumentException(
                    "benchmarkId must not be blank."
            );
        }
        observations = normalizeObservations(observations);
    }

    private static List<MarketIndexDailyObservation> normalizeObservations(
            List<MarketIndexDailyObservation> observations
    ) {
        Objects.requireNonNull(
                observations,
                "observations must not be null."
        );
        List<MarketIndexDailyObservation> sortedObservations =
                new ArrayList<>(observations.size());
        for (MarketIndexDailyObservation observation : observations) {
            sortedObservations.add(Objects.requireNonNull(
                    observation,
                    "observation must not be null."
            ));
        }
        sortedObservations.sort(Comparator.comparing(
                MarketIndexDailyObservation::observationDate
        ));
        for (int index = 1; index < sortedObservations.size(); index++) {
            if (sortedObservations.get(index - 1).observationDate()
                    .equals(sortedObservations.get(index).observationDate())) {
                throw new IllegalArgumentException(
                        "observations must not contain duplicate "
                                + "observationDate."
                );
            }
        }
        return List.copyOf(sortedObservations);
    }
}
