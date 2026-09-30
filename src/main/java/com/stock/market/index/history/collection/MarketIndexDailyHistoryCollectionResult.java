package com.stock.market.index.history.collection;

import com.stock.market.index.history.MarketIndexDailyHistoryRequest;

import java.time.LocalDate;
import java.util.Objects;

public record MarketIndexDailyHistoryCollectionResult(
        MarketIndexDailyHistoryCollectionStatus status,
        String benchmarkId,
        LocalDate requestedFromDate,
        LocalDate requestedToDate,
        LocalDate actualFromDate,
        int fetchedCount,
        int savedCount
) {
    public MarketIndexDailyHistoryCollectionResult {
        Objects.requireNonNull(status, "status must not be null.");
        if (benchmarkId == null || benchmarkId.isBlank()) {
            throw new IllegalArgumentException(
                    "benchmarkId must not be blank."
            );
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

    public static MarketIndexDailyHistoryCollectionResult collected(
            MarketIndexDailyHistoryRequest request,
            LocalDate actualFromDate,
            int fetchedCount
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        return new MarketIndexDailyHistoryCollectionResult(
                MarketIndexDailyHistoryCollectionStatus.COLLECTED,
                request.benchmarkId(),
                request.fromDate(),
                request.toDate(),
                actualFromDate,
                fetchedCount,
                fetchedCount
        );
    }

    public static MarketIndexDailyHistoryCollectionResult alreadyUpToDate(
            MarketIndexDailyHistoryRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        return new MarketIndexDailyHistoryCollectionResult(
                MarketIndexDailyHistoryCollectionStatus.ALREADY_UP_TO_DATE,
                request.benchmarkId(),
                request.fromDate(),
                request.toDate(),
                null,
                0,
                0
        );
    }

    private static void validateStatusFields(
            MarketIndexDailyHistoryCollectionStatus status,
            LocalDate requestedFromDate,
            LocalDate requestedToDate,
            LocalDate actualFromDate,
            int fetchedCount,
            int savedCount
    ) {
        if (status == MarketIndexDailyHistoryCollectionStatus.COLLECTED) {
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
