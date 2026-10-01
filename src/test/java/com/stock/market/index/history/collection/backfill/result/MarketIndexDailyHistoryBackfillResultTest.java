package com.stock.market.index.history.collection.backfill.result;

import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketIndexDailyHistoryBackfillResultTest {
    private static final LocalDate FROM = LocalDate.of(2023, 9, 25);
    private static final LocalDate TO = LocalDate.of(2025, 9, 29);
    private static final MarketIndexDailyHistoryRequest REQUEST =
            new MarketIndexDailyHistoryRequest("KOSPI", FROM, TO);

    @Test
    void retainsActualReturnedRangeAndZeroSavesWhenRowsAlreadyExist() {
        var result = new MarketIndexDailyHistoryBackfillResult(
                MarketIndexDailyHistoryBackfillStatus.BACKFILLED,
                REQUEST, REQUEST, 2, 0, FROM.plusDays(1), TO.minusDays(1)
        );

        assertThat(result.fetchedFromDate()).isEqualTo(FROM.plusDays(1));
        assertThat(result.fetchedToDate()).isEqualTo(TO.minusDays(1));
        assertThat(result.savedCount()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"-1, 0", "2, -1", "1, 2"})
    void rejectsInvalidCounts(int fetched, int saved) {
        assertThatThrownBy(() -> new MarketIndexDailyHistoryBackfillResult(
                MarketIndexDailyHistoryBackfillStatus.BACKFILLED,
                REQUEST, REQUEST, fetched, saved, FROM, TO
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Backfill counts must satisfy 0 <= savedCount <= fetchedCount.");
    }

    @Test
    void rejectsCollectionRangeOnSkippedResult() {
        assertThatThrownBy(() -> new MarketIndexDailyHistoryBackfillResult(
                MarketIndexDailyHistoryBackfillStatus.NO_EARLIER_RANGE,
                REQUEST, REQUEST, 0, 0, null, null
        )).hasMessage("NO_EARLIER_RANGE must not contain collection data.");
    }

    @Test
    void rejectsBackfilledStatusWithoutObservations() {
        assertThatThrownBy(() -> new MarketIndexDailyHistoryBackfillResult(
                MarketIndexDailyHistoryBackfillStatus.BACKFILLED,
                REQUEST, REQUEST, 0, 0, null, null
        )).hasMessage("Empty backfill must not contain fetched dates or BACKFILLED status.");
    }

    @Test
    void rejectsFetchedDatesOnEmptyResponse() {
        assertThatThrownBy(() -> new MarketIndexDailyHistoryBackfillResult(
                MarketIndexDailyHistoryBackfillStatus.NO_DATA,
                REQUEST, REQUEST, 0, 0, FROM, TO
        )).hasMessage("Empty backfill must not contain fetched dates or BACKFILLED status.");
    }

    @Test
    void rejectsCollectionOutsideRequestedRange() {
        var outside = new MarketIndexDailyHistoryRequest("KOSPI", FROM, TO.plusDays(1));
        assertThatThrownBy(() -> new MarketIndexDailyHistoryBackfillResult(
                MarketIndexDailyHistoryBackfillStatus.BACKFILLED,
                REQUEST, outside, 1, 1, FROM, FROM
        )).hasMessage("collectionRange must be within requestedRange for the same benchmark.");
    }

    @Test
    void rejectsFetchedDatesOutsideActualCollectionRange() {
        assertThatThrownBy(() -> new MarketIndexDailyHistoryBackfillResult(
                MarketIndexDailyHistoryBackfillStatus.BACKFILLED,
                REQUEST, REQUEST, 1, 1, FROM.minusDays(1), FROM
        )).hasMessage("Fetched dates must be within collectionRange with BACKFILLED status.");
    }
}
