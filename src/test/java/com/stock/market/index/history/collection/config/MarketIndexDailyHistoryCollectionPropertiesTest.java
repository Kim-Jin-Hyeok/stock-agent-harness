package com.stock.market.index.history.collection.config;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MarketIndexDailyHistoryCollectionPropertiesTest {

    @Test
    void acceptsCollectionSettings() {
        MarketIndexDailyHistoryCollectionProperties properties =
                properties(List.of("KOSPI"));

        assertThat(properties.dailyBarAvailableAt())
                .isEqualTo(LocalTime.of(20, 10));
        assertThat(properties.benchmarkIds()).containsExactly("KOSPI");
        assertThat(properties.initialLookbackYears()).isEqualTo(3);
    }

    @Test
    void copiesConfiguredBenchmarkIds() {
        List<String> benchmarkIds = new ArrayList<>(List.of("KOSPI"));

        MarketIndexDailyHistoryCollectionProperties properties =
                properties(benchmarkIds);
        benchmarkIds.add("KOSDAQ");

        assertThat(properties.benchmarkIds()).containsExactly("KOSPI");
    }

    @Test
    void rejectsNullDailyBarAvailableAt() {
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new MarketIndexDailyHistoryCollectionProperties(
                                null,
                                List.of("KOSPI"),
                                3
                        ))
                .withMessage("dailyBarAvailableAt must not be null.");
    }

    @Test
    void rejectsNullBenchmarkIds() {
        assertThatNullPointerException()
                .isThrownBy(() -> properties(null))
                .withMessage(
                        "Market index daily history collection benchmarkIds "
                                + "must not be null."
                );
    }

    @Test
    void rejectsEmptyBenchmarkIds() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(List.of()))
                .withMessage(
                        "Market index daily history collection benchmarkIds "
                                + "must not be empty."
                );
    }

    @Test
    void rejectsBlankBenchmarkId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(List.of(" ")))
                .withMessage(
                        "Market index daily history collection benchmarkId "
                                + "must not be blank."
                );
    }

    @Test
    void rejectsDuplicateBenchmarkId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(List.of("KOSPI", "KOSPI")))
                .withMessage(
                        "Duplicate market index daily history collection "
                                + "benchmarkId: KOSPI"
                );
    }

    @Test
    void rejectsNonPositiveInitialLookbackYears() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new MarketIndexDailyHistoryCollectionProperties(
                                LocalTime.of(20, 10),
                                List.of("KOSPI"),
                                0
                        ))
                .withMessage("initialLookbackYears must be positive.");
    }

    private MarketIndexDailyHistoryCollectionProperties properties(
            List<String> benchmarkIds
    ) {
        return new MarketIndexDailyHistoryCollectionProperties(
                LocalTime.of(20, 10),
                benchmarkIds,
                3
        );
    }
}
