package com.stock.harness.tool;

import com.stock.market.MarketService;
import com.stock.market.MarketSnapshot;
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

    private HarnessToolExecutor executor() {
        return new HarnessToolExecutor(
                portfolioService(),
                marketService()
        );
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
