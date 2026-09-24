package com.stock.market.price.history.collection;

import com.stock.market.price.history.DailyPriceHistoryRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DailyPriceHistoryCollectionResultTest {
    private static final DailyPriceHistoryRequest REQUEST =
            new DailyPriceHistoryRequest(
                    "005930",
                    LocalDate.of(2026, 9, 24),
                    LocalDate.of(2026, 9, 25)
            );

    @Test
    void createsCollectedResult() {
        DailyPriceHistoryCollectionResult result =
                DailyPriceHistoryCollectionResult.collected(
                        REQUEST,
                        REQUEST.fromDate(),
                        2
                );

        assertThat(result.status())
                .isEqualTo(DailyPriceHistoryCollectionStatus.COLLECTED);
        assertThat(result.symbol()).isEqualTo(REQUEST.symbol());
        assertThat(result.actualFromDate()).isEqualTo(REQUEST.fromDate());
        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(2);
    }

    @Test
    void createsAlreadyUpToDateResultWithoutCollectionData() {
        DailyPriceHistoryCollectionResult result =
                DailyPriceHistoryCollectionResult.alreadyUpToDate(REQUEST);

        assertThat(result.status()).isEqualTo(
                DailyPriceHistoryCollectionStatus.ALREADY_UP_TO_DATE
        );
        assertThat(result.actualFromDate()).isNull();
        assertThat(result.fetchedCount()).isZero();
        assertThat(result.savedCount()).isZero();
    }
}
