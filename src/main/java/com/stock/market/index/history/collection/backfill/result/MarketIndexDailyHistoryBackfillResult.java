package com.stock.market.index.history.collection.backfill.result;

import com.stock.market.index.history.MarketIndexDailyHistoryRequest;

import java.time.LocalDate;
import java.util.Objects;

public record MarketIndexDailyHistoryBackfillResult(
        MarketIndexDailyHistoryBackfillStatus status,
        MarketIndexDailyHistoryRequest requestedRange,
        MarketIndexDailyHistoryRequest collectionRange,
        int fetchedCount,
        int savedCount,
        LocalDate fetchedFromDate,
        LocalDate fetchedToDate
) {
    public MarketIndexDailyHistoryBackfillResult {
        Objects.requireNonNull(status, "status must not be null.");
        Objects.requireNonNull(requestedRange, "requestedRange must not be null.");
        if (fetchedCount < 0 || savedCount < 0 || savedCount > fetchedCount) {
            throw new IllegalArgumentException(
                    "Backfill counts must satisfy 0 <= savedCount <= fetchedCount."
            );
        }
        if (status == MarketIndexDailyHistoryBackfillStatus.NO_EARLIER_RANGE) {
            if (collectionRange != null || fetchedCount != 0 || savedCount != 0) {
                throw new IllegalArgumentException(
                        "NO_EARLIER_RANGE must not contain collection data."
                );
            }
        } else {
            Objects.requireNonNull(collectionRange, "collectionRange must not be null.");
            if (!requestedRange.benchmarkId().equals(collectionRange.benchmarkId())
                    || !requestedRange.fromDate().equals(collectionRange.fromDate())
                    || collectionRange.toDate().isAfter(requestedRange.toDate())) {
                throw new IllegalArgumentException(
                        "collectionRange must be within requestedRange for the same benchmark."
                );
            }
        }
        if (fetchedCount == 0) {
            if (fetchedFromDate != null || fetchedToDate != null
                    || status == MarketIndexDailyHistoryBackfillStatus.BACKFILLED) {
                throw new IllegalArgumentException(
                        "Empty backfill must not contain fetched dates or BACKFILLED status."
                );
            }
        } else {
            Objects.requireNonNull(fetchedFromDate, "fetchedFromDate must not be null.");
            Objects.requireNonNull(fetchedToDate, "fetchedToDate must not be null.");
            if (status != MarketIndexDailyHistoryBackfillStatus.BACKFILLED
                    || fetchedFromDate.isBefore(collectionRange.fromDate())
                    || fetchedToDate.isAfter(collectionRange.toDate())
                    || fetchedFromDate.isAfter(fetchedToDate)) {
                throw new IllegalArgumentException(
                        "Fetched dates must be within collectionRange with BACKFILLED status."
                );
            }
        }
    }
}
