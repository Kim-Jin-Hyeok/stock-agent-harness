package com.stock.harness;

import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;

public record HarnessRunContext(
        String runId,
        PortfolioSnapshot portfolioSnapshot,
        MarketSnapshot marketSnapshot
) {
}
