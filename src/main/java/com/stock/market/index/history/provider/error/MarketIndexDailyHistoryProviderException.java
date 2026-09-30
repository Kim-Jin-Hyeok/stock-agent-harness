package com.stock.market.index.history.provider.error;

import java.util.Objects;

public class MarketIndexDailyHistoryProviderException
        extends RuntimeException {
    private final MarketIndexDailyHistoryProviderFailureType failureType;

    public MarketIndexDailyHistoryProviderException(
            MarketIndexDailyHistoryProviderFailureType failureType,
            String message
    ) {
        super(message);
        this.failureType = Objects.requireNonNull(
                failureType,
                "failureType must not be null."
        );
    }

    public MarketIndexDailyHistoryProviderException(
            MarketIndexDailyHistoryProviderFailureType failureType,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureType = Objects.requireNonNull(
                failureType,
                "failureType must not be null."
        );
    }

    public MarketIndexDailyHistoryProviderFailureType failureType() {
        return failureType;
    }
}
