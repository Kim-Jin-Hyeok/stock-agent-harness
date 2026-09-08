package com.stock.harness.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessToolExecutionResultTest {

    @Test
    void createsExecutedResult() {
        HarnessToolExecutionResult result = executedPortfolioToolResult();

        assertThat(result.status()).isEqualTo(executedStatus());
        assertThat(result.type()).isEqualTo(HarnessToolType.GET_PORTFOLIO);
        assertThat(result.reasonCode()).isEqualTo(toolExecutedReasonCode());
        assertThat(result.reason()).isEqualTo(toolExecutedReason());
    }

    @Test
    void createsNotSupportedResult() {
        HarnessToolExecutionResult result = notSupportedPortfolioToolResult();

        assertThat(result.status()).isEqualTo(failedStatus());
        assertThat(result.type()).isEqualTo(HarnessToolType.GET_PORTFOLIO);
        assertThat(result.reasonCode()).isEqualTo(toolNotSupportedReasonCode());
        assertThat(result.reason()).isEqualTo(toolNotSupportedReason());
    }

    @Test
    void createsAuthorizationDeniedResult() {
        HarnessToolExecutionResult result = authorizationDeniedPortfolioToolResult();

        assertThat(result.status()).isEqualTo(failedStatus());
        assertThat(result.type()).isEqualTo(HarnessToolType.GET_PORTFOLIO);
        assertThat(result.reasonCode()).isEqualTo(toolAuthorizationDeniedReasonCode());
        assertThat(result.reason()).isEqualTo(toolAuthorizationDeniedReason());
    }

    private HarnessToolExecutionResult executedPortfolioToolResult() {
        return HarnessToolExecutionResult.executed(portfolioTool());
    }

    private HarnessToolExecutionResult notSupportedPortfolioToolResult() {
        return HarnessToolExecutionResult.notSupported(portfolioTool());
    }

    private HarnessToolExecutionResult authorizationDeniedPortfolioToolResult() {
        return HarnessToolExecutionResult.authorizationDenied(portfolioTool());
    }

    private HarnessToolType portfolioTool() {
        return HarnessToolType.GET_PORTFOLIO;
    }

    private HarnessToolExecutionStatus executedStatus() {
        return HarnessToolExecutionStatus.EXECUTED;
    }

    private HarnessToolExecutionStatus failedStatus() {
        return HarnessToolExecutionStatus.FAILED;
    }

    private HarnessToolExecutionReasonCode toolExecutedReasonCode() {
        return HarnessToolExecutionReasonCode.TOOL_EXECUTED;
    }

    private HarnessToolExecutionReasonCode toolNotSupportedReasonCode() {
        return HarnessToolExecutionReasonCode.TOOL_NOT_SUPPORTED;
    }

    private HarnessToolExecutionReasonCode toolAuthorizationDeniedReasonCode() {
        return HarnessToolExecutionReasonCode.TOOL_AUTHORIZATION_DENIED;
    }

    private String toolExecutedReason() {
        return "Harness tool execution completed.";
    }

    private String toolNotSupportedReason() {
        return "Tool execution is not supported yet.";
    }

    private String toolAuthorizationDeniedReason() {
        return "Tool authorization denied.";
    }
}
