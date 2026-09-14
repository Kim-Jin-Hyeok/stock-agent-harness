package com.stock.harness.tool;

import com.stock.market.MarketSnapshot;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.lookup.CurrentPriceLookupResult;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.PortfolioSnapshot;

public record HarnessToolOutput(
        HarnessToolType type,
        PortfolioSnapshot portfolioSnapshot,
        MarketSnapshot marketSnapshot,
        CurrentPriceSnapshot currentPriceSnapshot,
        CurrentPriceLookupSource currentPriceSource
) {
    public HarnessToolOutput(
            HarnessToolType type,
            PortfolioSnapshot portfolioSnapshot,
            MarketSnapshot marketSnapshot
    ) {
        this(type, portfolioSnapshot, marketSnapshot, null, null);
    }

    public HarnessToolOutput(
            HarnessToolType type,
            PortfolioSnapshot portfolioSnapshot,
            MarketSnapshot marketSnapshot,
            CurrentPriceSnapshot currentPriceSnapshot
    ) {
        this(type, portfolioSnapshot, marketSnapshot, currentPriceSnapshot, null);
    }

    public static HarnessToolOutput portfolio(PortfolioSnapshot portfolioSnapshot) {
        return new HarnessToolOutput(
                HarnessToolType.GET_PORTFOLIO,
                portfolioSnapshot,
                null,
                null,
                null
        );
    }

    public static HarnessToolOutput market(MarketSnapshot marketSnapshot) {
        return new HarnessToolOutput(
                HarnessToolType.GET_MARKET,
                null,
                marketSnapshot,
                null,
                null
        );
    }

    public static HarnessToolOutput currentPrice(CurrentPriceSnapshot currentPriceSnapshot) {
        return new HarnessToolOutput(
                HarnessToolType.GET_CURRENT_PRICE,
                null,
                null,
                currentPriceSnapshot,
                null
        );
    }

    public static HarnessToolOutput currentPrice(CurrentPriceLookupResult lookupResult) {
        return new HarnessToolOutput(
                HarnessToolType.GET_CURRENT_PRICE,
                null,
                null,
                lookupResult.snapshot(),
                lookupResult.source()
        );
    }
}
