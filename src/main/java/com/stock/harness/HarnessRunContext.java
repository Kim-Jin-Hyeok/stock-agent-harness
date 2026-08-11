package com.stock.harness;

import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;

public record HarnessRunContext(
        String runId,
        int maxSteps,
        PortfolioSnapshot portfolioSnapshot,
        MarketSnapshot marketSnapshot
) {
}
