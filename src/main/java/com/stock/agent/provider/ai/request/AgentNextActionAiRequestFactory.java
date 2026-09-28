package com.stock.agent.provider.ai.request;

import com.stock.harness.HarnessRunContext;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class AgentNextActionAiRequestFactory {

    public AgentNextActionAiRequest create(HarnessRunContext context) {
        Objects.requireNonNull(context, "context must not be null.");

        InvestmentStrategyIdentity strategy = context.strategyIdentity();
        return new AgentNextActionAiRequest(
                strategy.strategyId(),
                strategy.strategyVersion(),
                strategy.horizon(),
                context.allowedTools().types(),
                context.candidateSymbols(),
                context.portfolioSnapshot(),
                context.marketSnapshot(),
                context.toolResults()
        );
    }
}
