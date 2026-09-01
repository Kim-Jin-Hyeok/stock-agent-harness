package com.stock.harness.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessToolAuthorizerTest {

    @Test
    void allowsRequestWhenToolIsAllowed() {
        HarnessAllowedTools allowedTools = readOnlyAllowedTools();
        HarnessToolRequest request = getMarketRequest();

        HarnessToolAuthorizer authorizer = authorizer();
        HarnessToolAuthorizationResult result = authorizer.authorize(
                allowedTools,
                request
        );

        assertThat(result.status()).isEqualTo(HarnessToolAuthorizationStatus.ALLOWED);
        assertThat(result.type()).isEqualTo(getMarketTool());
        assertThat(result.reasonCode()).isEqualTo(HarnessToolAuthorizationReasonCode.TOOL_ALLOWED);
        assertThat(result.reason()).isEqualTo(toolAllowedReason());
    }

    @Test
    void deniesRequestWhenToolIsNotAllowed() {
        HarnessAllowedTools allowedTools = marketOnlyAllowedTools();
        HarnessToolRequest request = getPortfolioRequest();

        HarnessToolAuthorizer authorizer = authorizer();
        HarnessToolAuthorizationResult result = authorizer.authorize(
                allowedTools,
                request
        );

        assertThat(result.status()).isEqualTo(HarnessToolAuthorizationStatus.DENIED);
        assertThat(result.type()).isEqualTo(getPortfolioTool());
        assertThat(result.reasonCode()).isEqualTo(HarnessToolAuthorizationReasonCode.TOOL_NOT_ALLOWED);
        assertThat(result.reason()).isEqualTo(toolNotAllowedReason());
    }

    private HarnessToolAuthorizer authorizer() {
        return new HarnessToolAuthorizer();
    }

    private HarnessAllowedTools readOnlyAllowedTools() {
        return HarnessAllowedTools.readOnly();
    }

    private HarnessAllowedTools marketOnlyAllowedTools() {
        return new HarnessAllowedTools(
                List.of(getMarketTool())
        );
    }

    private HarnessToolRequest getPortfolioRequest() {
        return new HarnessToolRequest(getPortfolioTool());
    }

    private HarnessToolRequest getMarketRequest() {
        return new HarnessToolRequest(getMarketTool());
    }

    private HarnessToolType getPortfolioTool() {
        return HarnessToolType.GET_PORTFOLIO;
    }

    private HarnessToolType getMarketTool() {
        return HarnessToolType.GET_MARKET;
    }

    private String toolAllowedReason() {
        return "Harness tool authorization allowed.";
    }

    private String toolNotAllowedReason() {
        return "Harness tool is not allowed.";
    }
}
