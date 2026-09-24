package com.stock.market.price.history;

import java.time.LocalDate;
import java.util.Objects;

public record DailyPriceHistoryRequest(
        String symbol,
        LocalDate fromDate,
        LocalDate toDate
) {
    public DailyPriceHistoryRequest {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
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
