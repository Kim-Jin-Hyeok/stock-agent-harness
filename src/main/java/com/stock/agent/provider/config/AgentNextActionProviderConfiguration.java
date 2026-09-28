package com.stock.agent.provider.config;

import com.stock.agent.InvestmentAgent;
import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContextFactory;
import com.stock.agent.decision.movingaverage.provider.MovingAverageOrderDecisionProvider;
import com.stock.agent.decision.movingaverage.resolution.MovingAverageOrderDecisionResolver;
import com.stock.agent.provider.AgentNextActionProvider;
import com.stock.agent.provider.ai.AiAgentNextActionProvider;
import com.stock.agent.provider.ai.execution.AgentNextActionAiExecutor;
import com.stock.agent.provider.ai.prompt.AgentNextActionAiPromptFactory;
import com.stock.agent.provider.ai.request.AgentNextActionAiRequestFactory;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentNextActionProviderConfiguration {

    @Bean
    public AgentNextActionProvider agentNextActionProvider(
            AgentNextActionProviderProperties properties,
            MovingAverageAnalysisService movingAverageAnalysisService,
            MovingAverageOrderDecisionContextFactory decisionContextFactory,
            MovingAverageOrderDecisionProvider decisionProvider,
            MovingAverageOrderDecisionResolver decisionResolver,
            AgentNextActionAiRequestFactory requestFactory,
            AgentNextActionAiPromptFactory promptFactory,
            ObjectProvider<AgentNextActionAiExecutor> executorProvider
    ) {
        return switch (properties.providerType()) {
            case RULE_BASED -> new InvestmentAgent(
                    movingAverageAnalysisService,
                    decisionContextFactory,
                    decisionProvider,
                    decisionResolver
            );
            case AI -> new AiAgentNextActionProvider(
                    requestFactory,
                    promptFactory,
                    requireExecutor(executorProvider)
            );
        };
    }

    private AgentNextActionAiExecutor requireExecutor(
            ObjectProvider<AgentNextActionAiExecutor> executorProvider
    ) {
        AgentNextActionAiExecutor executor =
                executorProvider.getIfAvailable();
        if (executor == null) {
            throw new IllegalStateException(
                    "AgentNextActionAiExecutor must be configured when "
                            + "provider type is AI."
            );
        }
        return executor;
    }
}
