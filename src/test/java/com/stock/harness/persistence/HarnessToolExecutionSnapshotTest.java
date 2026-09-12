package com.stock.harness.persistence;

import com.stock.harness.tool.HarnessToolExecutionReasonCode;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolExecutionStatus;
import com.stock.harness.tool.HarnessToolOutput;
import com.stock.harness.tool.HarnessToolType;
import com.stock.market.MarketSnapshot;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessToolExecutionSnapshotTest {

    @Test
    void fromConvertsPortfolioToolExecutionResult() {
        HarnessToolExecutionSnapshot snapshot = HarnessToolExecutionSnapshot.from(
                HarnessToolExecutionResult.executed(
                        HarnessToolOutput.portfolio(portfolioSnapshot())
                )
        );

        assertThat(snapshot.status()).isEqualTo(HarnessToolExecutionStatus.EXECUTED);
        assertThat(snapshot.type()).isEqualTo(HarnessToolType.GET_PORTFOLIO);
        assertThat(snapshot.reasonCode()).isEqualTo(HarnessToolExecutionReasonCode.TOOL_EXECUTED);
        assertThat(snapshot.reason()).isEqualTo("Harness tool execution completed.");
        assertThat(snapshot.portfolioSnapshot()).isEqualTo(
                HarnessPortfolioSnapshot.from(portfolioSnapshot())
        );
        assertThat(snapshot.marketSnapshot()).isNull();
    }

    @Test
    void fromConvertsMarketToolExecutionResult() {
        HarnessToolExecutionSnapshot snapshot = HarnessToolExecutionSnapshot.from(
                HarnessToolExecutionResult.executed(
                        HarnessToolOutput.market(marketSnapshot())
                )
        );

        assertThat(snapshot.status()).isEqualTo(HarnessToolExecutionStatus.EXECUTED);
        assertThat(snapshot.type()).isEqualTo(HarnessToolType.GET_MARKET);
        assertThat(snapshot.marketSnapshot()).isEqualTo(
                HarnessMarketSnapshot.from(marketSnapshot())
        );
        assertThat(snapshot.portfolioSnapshot()).isNull();
    }

    @Test
    void fromConvertsCurrentPriceToolExecutionResult() {
        CurrentPriceSnapshot currentPrice = new CurrentPriceSnapshot("005930", 70_000L);

        HarnessToolExecutionSnapshot snapshot = HarnessToolExecutionSnapshot.from(
                HarnessToolExecutionResult.executed(
                        HarnessToolOutput.currentPrice(currentPrice)
                )
        );

        assertThat(snapshot.type()).isEqualTo(HarnessToolType.GET_CURRENT_PRICE);
        assertThat(snapshot.currentPriceSnapshot()).isEqualTo(
                HarnessCurrentPriceSnapshot.from(currentPrice)
        );
        assertThat(snapshot.portfolioSnapshot()).isNull();
        assertThat(snapshot.marketSnapshot()).isNull();
    }

    @Test
    void fromHandlesExecutionResultWithoutOutput() {
        HarnessToolExecutionSnapshot snapshot = HarnessToolExecutionSnapshot.from(
                HarnessToolExecutionResult.notSupported(HarnessToolType.GET_MARKET)
        );

        assertThat(snapshot.status()).isEqualTo(HarnessToolExecutionStatus.FAILED);
        assertThat(snapshot.type()).isEqualTo(HarnessToolType.GET_MARKET);
        assertThat(snapshot.reasonCode()).isEqualTo(HarnessToolExecutionReasonCode.TOOL_NOT_SUPPORTED);
        assertThat(snapshot.portfolioSnapshot()).isNull();
        assertThat(snapshot.marketSnapshot()).isNull();
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
                "KR",
                true,
                "Korean market is open."
        );
    }
}
