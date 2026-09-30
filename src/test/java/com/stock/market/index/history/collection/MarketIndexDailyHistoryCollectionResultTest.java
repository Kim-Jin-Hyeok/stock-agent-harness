package com.stock.market.index.history.collection;

import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MarketIndexDailyHistoryCollectionResultTest {
    private static final MarketIndexDailyHistoryRequest REQUEST =
            new MarketIndexDailyHistoryRequest(
                    "KOSPI",
                    LocalDate.of(2026, 9, 29),
                    LocalDate.of(2026, 9, 30)
            );

    @Test
    void createsCollectedResult() {
        MarketIndexDailyHistoryCollectionResult result =
                MarketIndexDailyHistoryCollectionResult.collected(
                        REQUEST,
                        REQUEST.fromDate(),
                        2
                );

        assertThat(result.status()).isEqualTo(
                MarketIndexDailyHistoryCollectionStatus.COLLECTED
        );
        assertThat(result.benchmarkId())
                .isEqualTo(REQUEST.benchmarkId());
        assertThat(result.actualFromDate())
                .isEqualTo(REQUEST.fromDate());
        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(2);
    }

    @Test
    void createsAlreadyUpToDateResultWithoutCollectionData() {
        MarketIndexDailyHistoryCollectionResult result =
                MarketIndexDailyHistoryCollectionResult
                        .alreadyUpToDate(REQUEST);

        assertThat(result.status()).isEqualTo(
                MarketIndexDailyHistoryCollectionStatus.ALREADY_UP_TO_DATE
        );
        assertThat(result.actualFromDate()).isNull();
        assertThat(result.fetchedCount()).isZero();
        assertThat(result.savedCount()).isZero();
    }
}
