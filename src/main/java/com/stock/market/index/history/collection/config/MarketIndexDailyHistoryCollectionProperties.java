package com.stock.market.index.history.collection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@ConfigurationProperties(prefix = "market.index.history.collection")
public record MarketIndexDailyHistoryCollectionProperties(
        LocalTime dailyBarAvailableAt,
        List<String> benchmarkIds,
        int initialLookbackYears
) {
    public MarketIndexDailyHistoryCollectionProperties {
        Objects.requireNonNull(
                dailyBarAvailableAt,
                "dailyBarAvailableAt must not be null."
        );
        benchmarkIds = List.copyOf(Objects.requireNonNull(
                benchmarkIds,
                "Market index daily history collection benchmarkIds "
                        + "must not be null."
        ));
        if (benchmarkIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Market index daily history collection benchmarkIds "
                            + "must not be empty."
            );
        }

        Set<String> uniqueBenchmarkIds = new HashSet<>();
        for (String benchmarkId : benchmarkIds) {
            if (benchmarkId == null || benchmarkId.isBlank()) {
                throw new IllegalArgumentException(
                        "Market index daily history collection benchmarkId "
                                + "must not be blank."
                );
            }
            if (!uniqueBenchmarkIds.add(benchmarkId)) {
                throw new IllegalArgumentException(
                        "Duplicate market index daily history collection "
                                + "benchmarkId: " + benchmarkId
                );
            }
        }

        if (initialLookbackYears <= 0) {
            throw new IllegalArgumentException(
                    "initialLookbackYears must be positive."
            );
        }
    }
}
