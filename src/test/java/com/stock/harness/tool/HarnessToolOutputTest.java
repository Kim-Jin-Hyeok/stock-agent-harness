package com.stock.harness.tool;

import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessToolOutputTest {

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
