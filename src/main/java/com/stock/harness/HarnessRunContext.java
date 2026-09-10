package com.stock.harness;

import com.stock.harness.tool.HarnessAllowedTools;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;

import java.util.ArrayList;
import java.util.List;

public record HarnessRunContext(
        String runId,
        HarnessRunLimits limits,
        HarnessAllowedTools allowedTools,
        PortfolioSnapshot portfolioSnapshot,
        MarketSnapshot marketSnapshot,
        List<HarnessToolExecutionResult> toolResults
) {
    public HarnessRunContext {
        toolResults = List.copyOf(toolResults);
    }

    public HarnessRunContext withToolResult(HarnessToolExecutionResult toolResult) {
        List<HarnessToolExecutionResult> updatedToolResults = new ArrayList<>(toolResults);
        updatedToolResults.add(toolResult);

        return new HarnessRunContext(
                runId,
                limits,
                allowedTools,
                portfolioSnapshot,
                marketSnapshot,
                updatedToolResults
        );
    }
}
