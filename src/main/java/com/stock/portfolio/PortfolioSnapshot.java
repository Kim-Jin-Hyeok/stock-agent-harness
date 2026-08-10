package com.stock.portfolio;

public record PortfolioSnapshot(
        long cashAmount,
        long totalAssetAmount
) {
}
