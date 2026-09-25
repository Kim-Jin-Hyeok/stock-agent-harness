package com.stock.market.price.history.provider.kis;

import com.stock.market.price.history.provider.error.DailyPriceHistoryProviderException;
import com.stock.market.price.history.provider.error.DailyPriceHistoryProviderFailureType;

import java.time.Duration;
import java.util.Objects;

public class KisDailyPriceHistoryRequestWaiter {
    private final Duration requestDelay;

    public KisDailyPriceHistoryRequestWaiter(Duration requestDelay) {
        this.requestDelay = Objects.requireNonNull(
                requestDelay,
                "requestDelay must not be null."
        );
        if (requestDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "requestDelay must not be negative."
            );
        }
    }

    public void waitBeforeRequest() {
        try {
            Thread.sleep(requestDelay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DailyPriceHistoryProviderException(
                    DailyPriceHistoryProviderFailureType.TEMPORARY,
                    "KIS daily price history request wait interrupted.",
                    exception
            );
        }
    }
}
