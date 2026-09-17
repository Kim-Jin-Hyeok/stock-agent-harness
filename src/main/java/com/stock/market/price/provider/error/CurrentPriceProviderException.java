package com.stock.market.price.provider.error;

import java.util.Objects;

public class CurrentPriceProviderException extends RuntimeException {
    private final CurrentPriceProviderFailureType failureType;

    public CurrentPriceProviderException(
            CurrentPriceProviderFailureType failureType,
            String message
    ) {
        super(message);
        this.failureType = Objects.requireNonNull(failureType, "failureType must not be null.");
    }

    public CurrentPriceProviderException(
            CurrentPriceProviderFailureType failureType,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureType = Objects.requireNonNull(failureType, "failureType must not be null.");
    }

    public CurrentPriceProviderFailureType failureType() {
        return failureType;
    }
}
