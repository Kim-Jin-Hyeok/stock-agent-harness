package com.stock.harness.tool;

import com.stock.market.MarketSnapshot;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.lookup.CurrentPriceLookupResult;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.PortfolioSnapshot;

public record HarnessToolOutput(
        HarnessToolType type,
        PortfolioSnapshot portfolioSnapshot,
        MarketSnapshot marketSnapshot,
        CurrentPriceSnapshot currentPriceSnapshot,
        CurrentPriceLookupSource currentPriceSource,
        DailyPriceHistory dailyPriceHistory
) {
    public HarnessToolOutput(
            HarnessToolType type,
            PortfolioSnapshot portfolioSnapshot,
            MarketSnapshot marketSnapshot
    ) {
        this(type, portfolioSnapshot, marketSnapshot, null, null, null);
    }

    public HarnessToolOutput(
            HarnessToolType type,
            PortfolioSnapshot portfolioSnapshot,
            MarketSnapshot marketSnapshot,
            CurrentPriceSnapshot currentPriceSnapshot
    ) {
        this(
                type,
                portfolioSnapshot,
                marketSnapshot,
                currentPriceSnapshot,
                null,
                null
        );
    }

    public HarnessToolOutput(
            HarnessToolType type,
            PortfolioSnapshot portfolioSnapshot,
            MarketSnapshot marketSnapshot,
            CurrentPriceSnapshot currentPriceSnapshot,
            CurrentPriceLookupSource currentPriceSource
    ) {
        this(
                type,
                portfolioSnapshot,
                marketSnapshot,
                currentPriceSnapshot,
                currentPriceSource,
                null
        );
    }

    public static HarnessToolOutput portfolio(PortfolioSnapshot portfolioSnapshot) {
        return new HarnessToolOutput(
                HarnessToolType.GET_PORTFOLIO,
                portfolioSnapshot,
                null,
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
                null,
                null
        );
    }

    public static HarnessToolOutput currentPrice(CurrentPriceLookupResult lookupResult) {
        return new HarnessToolOutput(
                HarnessToolType.GET_CURRENT_PRICE,
                null,
                null,
                lookupResult.snapshot(),
                lookupResult.source(),
                null
        );
    }

    public static HarnessToolOutput dailyPriceHistory(
            DailyPriceHistory dailyPriceHistory
    ) {
        return new HarnessToolOutput(
                HarnessToolType.GET_DAILY_PRICE_HISTORY,
                null,
                null,
                null,
                null,
                dailyPriceHistory
        );
    }
}
