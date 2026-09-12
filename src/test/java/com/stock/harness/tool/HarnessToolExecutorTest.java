package com.stock.harness.tool;

import com.stock.market.MarketService;
import com.stock.market.MarketSnapshot;
import com.stock.market.price.CurrentPriceService;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.portfolio.PortfolioSnapshotStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessToolExecutorTest {

    @Test
    void executesPortfolioToolRequest() {
        HarnessToolExecutionResult result = executor().execute(portfolioToolRequest());

        assertThat(result.status()).isEqualTo(executedStatus());
        assertThat(result.type()).isEqualTo(portfolioTool());
        assertThat(result.reasonCode()).isEqualTo(toolExecutedReasonCode());
        assertThat(result.reason()).isEqualTo(toolExecutedReason());
        assertThat(result.output().type()).isEqualTo(portfolioTool());
        assertThat(result.output().portfolioSnapshot()).isEqualTo(portfolioSnapshot());
        assertThat(result.output().marketSnapshot()).isNull();
    }

    @Test
    void executesMarketToolRequest() {
        HarnessToolExecutionResult result = executor().execute(marketToolRequest());

        assertThat(result.status()).isEqualTo(executedStatus());
        assertThat(result.type()).isEqualTo(marketTool());
        assertThat(result.reasonCode()).isEqualTo(toolExecutedReasonCode());
        assertThat(result.reason()).isEqualTo(toolExecutedReason());
        assertThat(result.output().type()).isEqualTo(marketTool());
        assertThat(result.output().portfolioSnapshot()).isNull();
        assertThat(result.output().marketSnapshot()).isEqualTo(marketSnapshot());
    }

    @Test
    void executesCurrentPriceToolRequest() {
        HarnessToolExecutionResult result = executor().execute(
                HarnessToolRequest.currentPrice("005930")
        );

        assertThat(result.status()).isEqualTo(executedStatus());
        assertThat(result.type()).isEqualTo(HarnessToolType.GET_CURRENT_PRICE);
        assertThat(result.output().currentPriceSnapshot())
                .isEqualTo(new CurrentPriceSnapshot("005930", 100_000L));
        assertThat(result.output().portfolioSnapshot()).isNull();
        assertThat(result.output().marketSnapshot()).isNull();
    }

    private HarnessToolExecutor executor() {
        return new HarnessToolExecutor(
                portfolioService(),
                marketService(),
                currentPriceService()
        );
    }

    private CurrentPriceService currentPriceService() {
        return new CurrentPriceService();
    }

    private PortfolioService portfolioService() {
        return new PortfolioService(portfolioSnapshotStore());
    }

    private PortfolioSnapshotStore portfolioSnapshotStore() {
        return new PortfolioSnapshotStore();
    }

    private MarketService marketService() {
        return new MarketService();
    }

    private HarnessToolRequest portfolioToolRequest() {
        return new HarnessToolRequest(portfolioTool());
    }

    private HarnessToolRequest marketToolRequest() {
        return new HarnessToolRequest(marketTool());
    }

    private HarnessToolType portfolioTool() {
        return HarnessToolType.GET_PORTFOLIO;
    }

    private HarnessToolType marketTool() {
        return HarnessToolType.GET_MARKET;
    }

    private PortfolioSnapshot portfolioSnapshot() {
        return portfolioSnapshotStore().getCurrentSnapshot();
    }

    private MarketSnapshot marketSnapshot() {
        return marketService().getCurrentSnapshot();
    }

    private HarnessToolExecutionStatus executedStatus() {
        return HarnessToolExecutionStatus.EXECUTED;
    }

    private HarnessToolExecutionReasonCode toolExecutedReasonCode() {
        return HarnessToolExecutionReasonCode.TOOL_EXECUTED;
    }

    private String toolExecutedReason() {
        return "Harness tool execution completed.";
    }
}
