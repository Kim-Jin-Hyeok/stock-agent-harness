package com.stock.market.price.history.collection;

import com.stock.market.price.history.DailyPriceHistoryRequest;

import java.time.LocalDate;
import java.util.Objects;

public record DailyPriceHistoryCollectionResult(
        DailyPriceHistoryCollectionStatus status,
        String symbol,
        LocalDate requestedFromDate,
        LocalDate requestedToDate,
        LocalDate actualFromDate,
        int fetchedCount,
        int savedCount
) {
    public DailyPriceHistoryCollectionResult {
        Objects.requireNonNull(status, "status must not be null.");
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        Objects.requireNonNull(
                requestedFromDate,
                "requestedFromDate must not be null."
        );
        Objects.requireNonNull(
                requestedToDate,
                "requestedToDate must not be null."
        );
        if (requestedFromDate.isAfter(requestedToDate)) {
            throw new IllegalArgumentException(
                    "requestedFromDate must not be after requestedToDate."
            );
        }
        if (fetchedCount < 0) {
            throw new IllegalArgumentException(
                    "fetchedCount must not be negative."
            );
        }
        if (savedCount < 0 || savedCount > fetchedCount) {
            throw new IllegalArgumentException(
                    "savedCount must be between zero and fetchedCount."
            );
        }
        validateStatusFields(
                status,
                requestedFromDate,
                requestedToDate,
                actualFromDate,
                fetchedCount,
                savedCount
        );
    }

    public static DailyPriceHistoryCollectionResult collected(
            DailyPriceHistoryRequest request,
            LocalDate actualFromDate,
            int fetchedCount
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        return new DailyPriceHistoryCollectionResult(
                DailyPriceHistoryCollectionStatus.COLLECTED,
                request.symbol(),
                request.fromDate(),
                request.toDate(),
                actualFromDate,
                fetchedCount,
                fetchedCount
        );
    }

    public static DailyPriceHistoryCollectionResult alreadyUpToDate(
            DailyPriceHistoryRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        return new DailyPriceHistoryCollectionResult(
                DailyPriceHistoryCollectionStatus.ALREADY_UP_TO_DATE,
                request.symbol(),
                request.fromDate(),
                request.toDate(),
                null,
                0,
                0
        );
    }

    private static void validateStatusFields(
            DailyPriceHistoryCollectionStatus status,
            LocalDate requestedFromDate,
            LocalDate requestedToDate,
            LocalDate actualFromDate,
            int fetchedCount,
            int savedCount
    ) {
        if (status == DailyPriceHistoryCollectionStatus.COLLECTED) {
            Objects.requireNonNull(
                    actualFromDate,
                    "actualFromDate must not be null when collected."
            );
            if (actualFromDate.isBefore(requestedFromDate)
                    || actualFromDate.isAfter(requestedToDate)) {
                throw new IllegalArgumentException(
                        "actualFromDate must be within requested range."
                );
            }
            return;
        }

        if (actualFromDate != null || fetchedCount != 0 || savedCount != 0) {
            throw new IllegalArgumentException(
                    "already up-to-date result must not contain collection data."
            );
        }
    }
}
