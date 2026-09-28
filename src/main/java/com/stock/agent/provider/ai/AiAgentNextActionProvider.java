package com.stock.agent.provider.ai;

import com.stock.agent.AgentNextAction;
import com.stock.agent.provider.AgentNextActionProvider;
import com.stock.agent.provider.ai.execution.AgentNextActionAiExecutor;
import com.stock.agent.provider.ai.prompt.AgentNextActionAiPrompt;
import com.stock.agent.provider.ai.prompt.AgentNextActionAiPromptFactory;
import com.stock.agent.provider.ai.request.AgentNextActionAiRequest;
import com.stock.agent.provider.ai.request.AgentNextActionAiRequestFactory;
import com.stock.harness.HarnessRunContext;

import java.util.Objects;

public class AiAgentNextActionProvider
        implements AgentNextActionProvider {
    private final AgentNextActionAiRequestFactory requestFactory;
    private final AgentNextActionAiPromptFactory promptFactory;
    private final AgentNextActionAiExecutor executor;

    public AiAgentNextActionProvider(
            AgentNextActionAiRequestFactory requestFactory,
            AgentNextActionAiPromptFactory promptFactory,
            AgentNextActionAiExecutor executor
    ) {
        this.requestFactory = Objects.requireNonNull(
                requestFactory,
                "requestFactory must not be null."
        );
        this.promptFactory = Objects.requireNonNull(
                promptFactory,
                "promptFactory must not be null."
        );
        this.executor = Objects.requireNonNull(
                executor,
                "executor must not be null."
        );
    }

    @Override
    public AgentNextAction next(HarnessRunContext context) {
        Objects.requireNonNull(context, "context must not be null.");

        AgentNextActionAiRequest request = requestFactory.create(context);
        AgentNextActionAiPrompt prompt = promptFactory.create(request);
        return Objects.requireNonNull(
                executor.execute(prompt),
                "AI executor result must not be null."
        );
    }
}
