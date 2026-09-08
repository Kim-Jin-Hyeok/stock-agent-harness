package com.stock.harness.tool;

import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;

public record HarnessToolOutput(
        HarnessToolType type,
        PortfolioSnapshot portfolioSnapshot,
        MarketSnapshot marketSnapshot
) {
    public static HarnessToolOutput portfolio(PortfolioSnapshot portfolioSnapshot) {
        return new HarnessToolOutput(
                HarnessToolType.GET_PORTFOLIO,
                portfolioSnapshot,
                null
        );
    }

    public static HarnessToolOutput market(MarketSnapshot marketSnapshot) {
        return new HarnessToolOutput(
                HarnessToolType.GET_MARKET,
                null,
                marketSnapshot
        );
    }
}
