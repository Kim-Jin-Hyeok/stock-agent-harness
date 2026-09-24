package com.stock.harness;

import com.stock.harness.tool.HarnessAllowedTools;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record HarnessRunContext(
        String runId,
        InvestmentStrategyIdentity strategyIdentity,
        HarnessRunLimits limits,
        HarnessAllowedTools allowedTools,
        PortfolioSnapshot portfolioSnapshot,
        MarketSnapshot marketSnapshot,
        List<String> candidateSymbols,
        List<HarnessToolExecutionResult> toolResults
) {
    public HarnessRunContext {
        Objects.requireNonNull(strategyIdentity, "strategyIdentity must not be null.");
        candidateSymbols = List.copyOf(candidateSymbols);
        toolResults = List.copyOf(toolResults);
    }

    public HarnessRunContext withToolResult(HarnessToolExecutionResult toolResult) {
        List<HarnessToolExecutionResult> updatedToolResults = new ArrayList<>(toolResults);
        updatedToolResults.add(toolResult);

        return new HarnessRunContext(
                runId,
                strategyIdentity,
                limits,
                allowedTools,
                portfolioSnapshot,
                marketSnapshot,
                candidateSymbols,
                updatedToolResults
        );
    }
}
