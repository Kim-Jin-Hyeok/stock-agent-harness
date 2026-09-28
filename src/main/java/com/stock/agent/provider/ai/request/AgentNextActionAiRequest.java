package com.stock.agent.provider.ai.request;

import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolType;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.profile.InvestmentHorizon;

import java.util.List;
import java.util.Objects;

public record AgentNextActionAiRequest(
        String strategyId,
        int strategyVersion,
        InvestmentHorizon horizon,
        List<HarnessToolType> allowedToolTypes,
        List<String> candidateSymbols,
        PortfolioSnapshot portfolioSnapshot,
        MarketSnapshot marketSnapshot,
        List<HarnessToolExecutionResult> toolResults
) {
    public AgentNextActionAiRequest {
        if (strategyId == null || strategyId.isBlank()) {
            throw new IllegalArgumentException(
                    "strategyId must not be blank."
            );
        }
        if (strategyVersion < 1) {
            throw new IllegalArgumentException(
                    "strategyVersion must be at least 1."
            );
        }
        Objects.requireNonNull(horizon, "horizon must not be null.");
        allowedToolTypes = List.copyOf(Objects.requireNonNull(
                allowedToolTypes,
                "allowedToolTypes must not be null."
        ));
        candidateSymbols = List.copyOf(Objects.requireNonNull(
                candidateSymbols,
                "candidateSymbols must not be null."
        ));
        Objects.requireNonNull(
                portfolioSnapshot,
                "portfolioSnapshot must not be null."
        );
        Objects.requireNonNull(
                marketSnapshot,
                "marketSnapshot must not be null."
        );
        toolResults = List.copyOf(Objects.requireNonNull(
                toolResults,
                "toolResults must not be null."
        ));
    }
}
