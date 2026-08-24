package com.stock.harness.persistence;

import com.stock.portfolio.PortfolioPosition;

public record HarnessPortfolioPositionSnapshot(
        String symbol,
        long quantity,
        long averagePriceKrw,
        long marketValueKrw
) {
    public static HarnessPortfolioPositionSnapshot from(PortfolioPosition position) {
        return new HarnessPortfolioPositionSnapshot(
                position.symbol(),
                position.quantity(),
                position.averagePriceKrw(),
                position.marketValueKrw()
        );
    }
}
