package com.stock.harness.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessToolAuthorizationResultTest {

    @Test
    void createsAllowedResult() {
        HarnessToolAuthorizationResult result = allowedPortfolioToolResult();

        assertThat(result.status()).isEqualTo(HarnessToolAuthorizationStatus.ALLOWED);
        assertThat(result.type()).isEqualTo(HarnessToolType.GET_PORTFOLIO);
        assertThat(result.reasonCode()).isEqualTo(toolAllowedReasonCode());
        assertThat(result.reason()).isEqualTo(toolAllowedReason());
    }

    @Test
    void createsDeniedResult() {
        HarnessToolAuthorizationResult result = deniedPortfolioToolResult();

        assertThat(result.status()).isEqualTo(HarnessToolAuthorizationStatus.DENIED);
        assertThat(result.type()).isEqualTo(HarnessToolType.GET_PORTFOLIO);
        assertThat(result.reasonCode()).isEqualTo(toolNotAllowedReasonCode());
        assertThat(result.reason()).isEqualTo(toolNotAllowedReason());
    }

    private HarnessToolAuthorizationResult allowedPortfolioToolResult() {
        return HarnessToolAuthorizationResult.allowed(getPortfolioTool());
    }

    private HarnessToolAuthorizationResult deniedPortfolioToolResult() {
        return HarnessToolAuthorizationResult.denied(
                getPortfolioTool(),
                toolNotAllowedReasonCode(),
                toolNotAllowedReason()
        );
    }

    private HarnessToolType getPortfolioTool() {
        return HarnessToolType.GET_PORTFOLIO;
    }

    private HarnessToolAuthorizationReasonCode toolAllowedReasonCode() {
        return HarnessToolAuthorizationReasonCode.TOOL_ALLOWED;
    }

    private HarnessToolAuthorizationReasonCode toolNotAllowedReasonCode() {
        return HarnessToolAuthorizationReasonCode.TOOL_NOT_ALLOWED;
    }

    private String toolAllowedReason() {
        return "Harness tool authorization allowed.";
    }

    private String toolNotAllowedReason() {
        return "Harness tool is not allowed.";
    }
}
