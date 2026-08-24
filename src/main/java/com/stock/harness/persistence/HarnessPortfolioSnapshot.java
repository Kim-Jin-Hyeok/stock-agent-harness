package com.stock.harness.persistence;

import com.stock.portfolio.PortfolioSnapshot;

import java.util.List;

public record HarnessPortfolioSnapshot(
        long cashAmountKrw,
        long totalAssetAmountKrw,
        List<HarnessPortfolioPositionSnapshot> positions
) {
    public static HarnessPortfolioSnapshot from(PortfolioSnapshot portfolioSnapshot) {
        return new HarnessPortfolioSnapshot(
                portfolioSnapshot.cashAmountKrw(),
                portfolioSnapshot.totalAssetAmountKrw(),
                portfolioSnapshot.positions().stream()
                        .map(HarnessPortfolioPositionSnapshot::from)
                        .toList()
        );
    }
}
