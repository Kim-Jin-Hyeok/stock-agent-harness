package com.stock.harness.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessAllowedToolsTest {

    @Test
    void allowsReadOnlyTools() {
        HarnessAllowedTools allowedTools = readOnlyAllowedTools();

        assertThat(allowedTools.allows(getPortfolioTool())).isTrue();
        assertThat(allowedTools.allows(getMarketTool())).isTrue();
    }

    private HarnessAllowedTools readOnlyAllowedTools() {
        return HarnessAllowedTools.readOnly();
    }

    private HarnessToolType getPortfolioTool() {
        return HarnessToolType.GET_PORTFOLIO;
    }

    private HarnessToolType getMarketTool() {
        return HarnessToolType.GET_MARKET;
    }
}
