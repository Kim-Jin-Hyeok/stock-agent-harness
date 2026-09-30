package com.stock.market.index.history;

import java.time.LocalDate;
import java.util.Objects;

public record MarketIndexDailyHistoryRequest(
        String benchmarkId,
        LocalDate fromDate,
        LocalDate toDate
) {
    public MarketIndexDailyHistoryRequest {
        if (benchmarkId == null || benchmarkId.isBlank()) {
            throw new IllegalArgumentException(
                    "benchmarkId must not be blank."
            );
        }
        Objects.requireNonNull(fromDate, "fromDate must not be null.");
        Objects.requireNonNull(toDate, "toDate must not be null.");
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                    "fromDate must not be after toDate."
            );
        }
    }
}
