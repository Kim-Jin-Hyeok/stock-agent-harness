package com.stock.harness.persistence;

import com.stock.market.price.CurrentPriceSnapshot;

public record HarnessCurrentPriceSnapshot(
        String symbol,
        long priceKrw
) {
    public static HarnessCurrentPriceSnapshot from(CurrentPriceSnapshot snapshot) {
        return new HarnessCurrentPriceSnapshot(
                snapshot.symbol(),
                snapshot.priceKrw()
        );
    }
}
