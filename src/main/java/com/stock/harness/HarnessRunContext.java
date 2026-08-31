package com.stock.harness;

import com.stock.harness.tool.HarnessAllowedTools;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;

public record HarnessRunContext(
        String runId,
        HarnessRunLimits limits,
        HarnessAllowedTools allowedTools,
        PortfolioSnapshot portfolioSnapshot,
        MarketSnapshot marketSnapshot
) {
}
