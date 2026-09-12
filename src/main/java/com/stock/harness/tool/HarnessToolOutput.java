package com.stock.harness.tool;

import com.stock.market.MarketSnapshot;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.portfolio.PortfolioSnapshot;

public record HarnessToolOutput(
        HarnessToolType type,
        PortfolioSnapshot portfolioSnapshot,
        MarketSnapshot marketSnapshot,
        CurrentPriceSnapshot currentPriceSnapshot
) {
    public HarnessToolOutput(
            HarnessToolType type,
            PortfolioSnapshot portfolioSnapshot,
            MarketSnapshot marketSnapshot
    ) {
        this(type, portfolioSnapshot, marketSnapshot, null);
    }

    public static HarnessToolOutput portfolio(PortfolioSnapshot portfolioSnapshot) {
        return new HarnessToolOutput(
                HarnessToolType.GET_PORTFOLIO,
                portfolioSnapshot,
                null,
                null
        );
    }

    public static HarnessToolOutput market(MarketSnapshot marketSnapshot) {
        return new HarnessToolOutput(
                HarnessToolType.GET_MARKET,
                null,
                marketSnapshot,
                null
        );
    }

    public static HarnessToolOutput currentPrice(CurrentPriceSnapshot currentPriceSnapshot) {
        return new HarnessToolOutput(
                HarnessToolType.GET_CURRENT_PRICE,
                null,
                null,
                currentPriceSnapshot
        );
    }
}
