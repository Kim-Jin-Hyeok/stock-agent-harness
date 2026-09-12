package com.stock.harness.tool;

import com.stock.portfolio.PortfolioSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessToolExecutionResultTest {

    @Test
    void createsExecutedResult() {
        HarnessToolExecutionResult result = executedPortfolioToolResult();

        assertThat(result.status()).isEqualTo(executedStatus());
        assertThat(result.type()).isEqualTo(HarnessToolType.GET_PORTFOLIO);
        assertThat(result.reasonCode()).isEqualTo(toolExecutedReasonCode());
        assertThat(result.reason()).isEqualTo(toolExecutedReason());
        assertThat(result.output()).isEqualTo(portfolioToolOutput());
    }

    @Test
    void createsNotSupportedResult() {
        HarnessToolExecutionResult result = notSupportedPortfolioToolResult();

        assertThat(result.status()).isEqualTo(failedStatus());
        assertThat(result.type()).isEqualTo(HarnessToolType.GET_PORTFOLIO);
        assertThat(result.reasonCode()).isEqualTo(toolNotSupportedReasonCode());
        assertThat(result.reason()).isEqualTo(toolNotSupportedReason());
        assertThat(result.output()).isNull();
    }

    @Test
    void createsAuthorizationDeniedResult() {
        HarnessToolExecutionResult result = authorizationDeniedPortfolioToolResult();

        assertThat(result.status()).isEqualTo(failedStatus());
        assertThat(result.type()).isEqualTo(HarnessToolType.GET_PORTFOLIO);
        assertThat(result.reasonCode()).isEqualTo(toolAuthorizationDeniedReasonCode());
        assertThat(result.reason()).isEqualTo(toolAuthorizationDeniedReason());
        assertThat(result.output()).isNull();
    }

    @Test
    void createsDuplicateRequestResult() {
        HarnessToolExecutionResult result = HarnessToolExecutionResult.duplicateRequest(
                HarnessToolType.GET_PORTFOLIO
        );

        assertThat(result.status()).isEqualTo(HarnessToolExecutionStatus.SKIPPED);
        assertThat(result.type()).isEqualTo(HarnessToolType.GET_PORTFOLIO);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolExecutionReasonCode.DUPLICATE_TOOL_REQUEST
        );
        assertThat(result.reason()).isEqualTo("Duplicate tool request was skipped.");
        assertThat(result.output()).isNull();
    }

    private HarnessToolExecutionResult executedPortfolioToolResult() {
        return HarnessToolExecutionResult.executed(portfolioToolOutput());
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

    private HarnessToolOutput portfolioToolOutput() {
        return HarnessToolOutput.portfolio(portfolioSnapshot());
    }

    private PortfolioSnapshot portfolioSnapshot() {
        return new PortfolioSnapshot(
                5_000_000L,
                10_000_000L,
                List.of()
        );
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
