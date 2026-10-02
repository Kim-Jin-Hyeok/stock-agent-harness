package com.stock.market.price.history.collection.backfill.result;

import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.TradingVenueScope;

import java.util.Objects;

public record DailyPriceTradingValueBackfillResult(
        DailyPriceTradingValueBackfillStatus status,
        DailyPriceHistoryRequest requestedRange,
        DailyPriceHistoryRequest collectionRange,
        TradingVenueScope expectedVenueScope,
        int targetCount,
        int fetchedCount,
        int updatedCount
) {
    public DailyPriceTradingValueBackfillResult {
        Objects.requireNonNull(status, "status must not be null.");
        Objects.requireNonNull(requestedRange, "requestedRange must not be null.");
        Objects.requireNonNull(expectedVenueScope, "expectedVenueScope must not be null.");
        if (targetCount < 0 || fetchedCount < 0 || updatedCount < 0
                || updatedCount > targetCount || targetCount > fetchedCount) {
            throw new IllegalArgumentException("Backfill counts are inconsistent.");
        }
        if (status == DailyPriceTradingValueBackfillStatus.NO_TARGETS) {
            if (collectionRange != null || targetCount != 0
                    || fetchedCount != 0 || updatedCount != 0) {
                throw new IllegalArgumentException("NO_TARGETS must not contain collection data.");
            }
        } else {
            Objects.requireNonNull(collectionRange, "collectionRange must not be null.");
            if (targetCount == 0
                    || !requestedRange.symbol().equals(collectionRange.symbol())
                    || collectionRange.fromDate().isBefore(requestedRange.fromDate())
                    || collectionRange.toDate().isAfter(requestedRange.toDate())
                    || (status == DailyPriceTradingValueBackfillStatus.BACKFILLED
                        && updatedCount == 0)
                    || (status == DailyPriceTradingValueBackfillStatus.ALREADY_FILLED
                        && updatedCount != 0)) {
                throw new IllegalArgumentException("Backfill status or collection range is inconsistent.");
            }
        }
    }
}
