package com.stock.market.index.history.provider.kis;

import com.stock.market.index.history.provider.error.MarketIndexDailyHistoryProviderException;
import com.stock.market.index.history.provider.error.MarketIndexDailyHistoryProviderFailureType;

import java.time.Duration;
import java.util.Objects;

public class KisMarketIndexDailyHistoryRequestWaiter {
    private final Duration requestDelay;

    public KisMarketIndexDailyHistoryRequestWaiter(Duration requestDelay) {
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
            throw new MarketIndexDailyHistoryProviderException(
                    MarketIndexDailyHistoryProviderFailureType.TEMPORARY,
                    "KIS market index daily history request wait "
                            + "interrupted.",
                    exception
            );
        }
    }
}
