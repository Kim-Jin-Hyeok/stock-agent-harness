package com.stock.harness.persistence;

import com.stock.market.price.CurrentPriceSnapshot;

import java.time.Instant;

public record HarnessCurrentPriceSnapshot(
        String symbol,
        long priceKrw,
        Instant observedAt
) {
    public static HarnessCurrentPriceSnapshot from(CurrentPriceSnapshot snapshot) {
        return new HarnessCurrentPriceSnapshot(
                snapshot.symbol(),
                snapshot.priceKrw(),
                snapshot.observedAt()
        );
    }
}
