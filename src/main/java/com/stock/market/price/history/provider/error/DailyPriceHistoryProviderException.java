package com.stock.market.price.history.provider.error;

import java.util.Objects;

public class DailyPriceHistoryProviderException extends RuntimeException {
    private final DailyPriceHistoryProviderFailureType failureType;

    public DailyPriceHistoryProviderException(
            DailyPriceHistoryProviderFailureType failureType,
            String message
    ) {
        super(message);
        this.failureType = Objects.requireNonNull(
                failureType,
                "failureType must not be null."
        );
    }

    public DailyPriceHistoryProviderException(
            DailyPriceHistoryProviderFailureType failureType,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureType = Objects.requireNonNull(
                failureType,
                "failureType must not be null."
        );
    }

    public DailyPriceHistoryProviderFailureType failureType() {
        return failureType;
    }
}
