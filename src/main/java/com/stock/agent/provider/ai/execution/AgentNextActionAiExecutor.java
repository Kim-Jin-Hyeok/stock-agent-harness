package com.stock.agent.provider.ai.execution;

import com.stock.agent.AgentNextAction;
import com.stock.agent.provider.ai.prompt.AgentNextActionAiPrompt;

@FunctionalInterface
public interface AgentNextActionAiExecutor {
    AgentNextAction execute(AgentNextActionAiPrompt prompt);
}
