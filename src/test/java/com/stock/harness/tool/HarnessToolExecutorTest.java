package com.stock.harness.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessToolExecutorTest {

    @Test
    void returnsNotSupportedResultForPortfolioToolRequest() {
        HarnessToolExecutionResult result = executor().execute(portfolioToolRequest());

        assertThat(result.status()).isEqualTo(failedStatus());
        assertThat(result.type()).isEqualTo(portfolioTool());
        assertThat(result.reasonCode()).isEqualTo(toolNotSupportedReasonCode());
        assertThat(result.reason()).isEqualTo(toolNotSupportedReason());
    }

    @Test
    void returnsNotSupportedResultForMarketToolRequest() {
        HarnessToolExecutionResult result = executor().execute(marketToolRequest());

        assertThat(result.status()).isEqualTo(failedStatus());
        assertThat(result.type()).isEqualTo(marketTool());
        assertThat(result.reasonCode()).isEqualTo(toolNotSupportedReasonCode());
        assertThat(result.reason()).isEqualTo(toolNotSupportedReason());
    }

    private HarnessToolExecutor executor() {
        return new HarnessToolExecutor();
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

    private HarnessToolExecutionStatus failedStatus() {
        return HarnessToolExecutionStatus.FAILED;
    }

    private HarnessToolExecutionReasonCode toolNotSupportedReasonCode() {
        return HarnessToolExecutionReasonCode.TOOL_NOT_SUPPORTED;
    }

    private String toolNotSupportedReason() {
        return "Tool execution is not supported yet.";
    }
}
