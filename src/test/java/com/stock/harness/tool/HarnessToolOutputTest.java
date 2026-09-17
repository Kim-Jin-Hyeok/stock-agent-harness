package com.stock.harness.tool;

import com.stock.market.MarketSnapshot;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.lookup.CurrentPriceLookupResult;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.PortfolioSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessToolOutputTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void createsPortfolioOutput() {
        PortfolioSnapshot portfolioSnapshot = portfolioSnapshot();

        HarnessToolOutput output = HarnessToolOutput.portfolio(portfolioSnapshot);

        assertThat(output.type()).isEqualTo(portfolioTool());
        assertThat(output.portfolioSnapshot()).isEqualTo(portfolioSnapshot);
        assertThat(output.marketSnapshot()).isNull();
    }

    @Test
    void createsMarketOutput() {
        MarketSnapshot marketSnapshot = marketSnapshot();

        HarnessToolOutput output = HarnessToolOutput.market(marketSnapshot);

        assertThat(output.type()).isEqualTo(marketTool());
        assertThat(output.portfolioSnapshot()).isNull();
        assertThat(output.marketSnapshot()).isEqualTo(marketSnapshot);
    }

    @Test
    void createsCurrentPriceOutput() {
        CurrentPriceSnapshot currentPriceSnapshot = new CurrentPriceSnapshot(
                "005930",
                70_000L,
                OBSERVED_AT
        );

        HarnessToolOutput output = HarnessToolOutput.currentPrice(
                CurrentPriceLookupResult.provider(currentPriceSnapshot)
        );

        assertThat(output.type()).isEqualTo(HarnessToolType.GET_CURRENT_PRICE);
        assertThat(output.currentPriceSnapshot()).isEqualTo(currentPriceSnapshot);
        assertThat(output.currentPriceSource()).isEqualTo(CurrentPriceLookupSource.PROVIDER);
        assertThat(output.portfolioSnapshot()).isNull();
        assertThat(output.marketSnapshot()).isNull();
    }

    private PortfolioSnapshot portfolioSnapshot() {
        return new PortfolioSnapshot(
                5_000_000L,
                10_000_000L,
                List.of()
        );
    }

    private MarketSnapshot marketSnapshot() {
        return new MarketSnapshot(
                "KOSPI",
                true,
                "KOSPI market is open."
        );
    }

    private HarnessToolType portfolioTool() {
        return HarnessToolType.GET_PORTFOLIO;
    }

    private HarnessToolType marketTool() {
        return HarnessToolType.GET_MARKET;
    }
}
