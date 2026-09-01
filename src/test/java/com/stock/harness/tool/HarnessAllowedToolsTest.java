package com.stock.harness.tool;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessAllowedToolsTest {

    @Test
    void allowsReadOnlyTools() {
        HarnessAllowedTools allowedTools = readOnlyAllowedTools();

        assertThat(allowedTools.allows(getPortfolioTool())).isTrue();
        assertThat(allowedTools.allows(getMarketTool())).isTrue();
    }

    @Test
    void keepsAllowedToolsImmutableFromSourceList() {
        List<HarnessToolType> sourceTypes = new ArrayList<>();
        sourceTypes.add(getPortfolioTool());

        HarnessAllowedTools allowedTools = new HarnessAllowedTools(sourceTypes);

        sourceTypes.add(getMarketTool());

        assertThat(allowedTools.types())
                .containsExactly(getPortfolioTool());
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
