package com.stock.portfolio;

public record PortfolioPosition(
        String symbol,
        long quantity,
        long averagePriceKrw,
        long marketValueKrw
) {
}
