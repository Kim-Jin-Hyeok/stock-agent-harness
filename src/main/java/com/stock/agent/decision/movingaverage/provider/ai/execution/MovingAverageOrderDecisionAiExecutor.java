package com.stock.agent.decision.movingaverage.provider.ai.execution;

import com.stock.agent.decision.movingaverage.provider.ai.prompt.MovingAverageOrderDecisionAiPrompt;
import com.stock.agent.decision.movingaverage.provider.result.MovingAverageOrderDecisionProviderResult;

@FunctionalInterface
public interface MovingAverageOrderDecisionAiExecutor {
    MovingAverageOrderDecisionProviderResult execute(
            MovingAverageOrderDecisionAiPrompt prompt
    );
}
