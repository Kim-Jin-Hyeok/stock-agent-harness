package com.stock.harness.persistence;

import com.stock.market.MarketSnapshot;

public record HarnessMarketSnapshot(
        String market,
        boolean marketOpen,
        String description
) {
    public static HarnessMarketSnapshot from(MarketSnapshot snapshot) {
        return new HarnessMarketSnapshot(
                snapshot.market(),
                snapshot.marketOpen(),
                snapshot.description()
        );
    }
}
