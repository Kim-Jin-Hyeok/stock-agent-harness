package com.stock.portfolio;

import java.util.List;

public record PortfolioSnapshot(
        long cashAmountKrw,
        long totalAssetAmountKrw,
        List<PortfolioPosition> positions
) {
    public long positionMarketValueKrw(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return 0L;
        }

        return positions.stream()
                .filter(position -> symbol.equals(position.symbol()))
                .mapToLong(PortfolioPosition::marketValueKrw)
                .sum();
    }
}
