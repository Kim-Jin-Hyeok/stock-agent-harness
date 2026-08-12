package com.stock.portfolio;

import java.util.List;

public record PortfolioSnapshot(
        long cashAmountKrw,
        long totalAssetAmountKrw,
        List<PortfolioPosition> positions
) {
}
