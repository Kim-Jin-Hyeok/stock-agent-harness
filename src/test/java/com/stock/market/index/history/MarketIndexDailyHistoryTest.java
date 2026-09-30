package com.stock.market.index.history;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketIndexDailyHistoryTest {
    private static final LocalDate FIRST_DATE =
            LocalDate.of(2026, 9, 1);

    @Test
    void sortsAndCopiesObservations() {
        MarketIndexDailyObservation first = observation(0);
        MarketIndexDailyObservation second = observation(1);
        List<MarketIndexDailyObservation> mutableObservations =
                new ArrayList<>(List.of(second, first));

        MarketIndexDailyHistory history = new MarketIndexDailyHistory(
                "KOSPI",
                mutableObservations
        );
        mutableObservations.clear();

        assertThat(history.benchmarkId()).isEqualTo("KOSPI");
        assertThat(history.observations()).containsExactly(first, second);
        assertThatThrownBy(() -> history.observations().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void allowsEmptyHistory() {
        MarketIndexDailyHistory history = new MarketIndexDailyHistory(
                "KOSPI",
                List.of()
        );

        assertThat(history.observations()).isEmpty();
    }

    @Test
    void rejectsDuplicateObservationDate() {
        assertThatThrownBy(() -> new MarketIndexDailyHistory(
                "KOSPI",
                List.of(observation(0), observation(0))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "observations must not contain duplicate "
                                + "observationDate."
                );
    }

    @Test
    void rejectsNullObservation() {
        List<MarketIndexDailyObservation> observations =
                new ArrayList<>();
        observations.add(null);

        assertThatThrownBy(() -> new MarketIndexDailyHistory(
                "KOSPI",
                observations
        )).isInstanceOf(NullPointerException.class)
                .hasMessage("observation must not be null.");
    }

    private MarketIndexDailyObservation observation(int dayOffset) {
        return new MarketIndexDailyObservation(
                FIRST_DATE.plusDays(dayOffset),
                new BigDecimal("3421.37")
        );
    }
}
