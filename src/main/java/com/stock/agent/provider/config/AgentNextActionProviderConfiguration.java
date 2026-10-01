package com.stock.agent.provider.config;

import com.stock.agent.InvestmentAgent;
import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContextFactory;
import com.stock.agent.decision.movingaverage.provider.MovingAverageOrderDecisionProvider;
import com.stock.agent.decision.movingaverage.resolution.MovingAverageOrderDecisionResolver;
import com.stock.agent.decision.swing.v1.SwingV1DecisionService;
import com.stock.agent.provider.AgentNextActionProvider;
import com.stock.agent.provider.ai.AiAgentNextActionProvider;
import com.stock.agent.provider.ai.execution.AgentNextActionAiExecutor;
import com.stock.agent.provider.ai.prompt.AgentNextActionAiPromptFactory;
import com.stock.agent.provider.ai.request.AgentNextActionAiRequestFactory;
import com.stock.agent.provider.rulebased.StrategyRuleBasedAgentNextActionProvider;
import com.stock.agent.provider.rulebased.swing.v1.SwingV1AgentNextActionProvider;
import com.stock.market.calendar.MarketTradingDayPolicy;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class AgentNextActionProviderConfiguration {

    @Bean
    public AgentNextActionProvider agentNextActionProvider(
            AgentNextActionProviderProperties properties,
            MovingAverageAnalysisService movingAverageAnalysisService,
            MovingAverageOrderDecisionContextFactory decisionContextFactory,
            MovingAverageOrderDecisionProvider decisionProvider,
            MovingAverageOrderDecisionResolver decisionResolver,
            SwingV1DecisionService swingDecisionService,
            MarketTradingDayPolicy marketTradingDayPolicy,
            Clock clock,
            AgentNextActionAiRequestFactory requestFactory,
            AgentNextActionAiPromptFactory promptFactory,
            ObjectProvider<AgentNextActionAiExecutor> executorProvider
    ) {
        return switch (properties.providerType()) {
            case RULE_BASED -> ruleBasedProvider(
                    movingAverageAnalysisService,
                    decisionContextFactory,
                    decisionProvider,
                    decisionResolver,
                    swingDecisionService,
                    marketTradingDayPolicy,
                    clock
            );
            case AI -> new AiAgentNextActionProvider(
                    requestFactory,
                    promptFactory,
                    requireExecutor(executorProvider)
            );
        };
    }

    private AgentNextActionProvider ruleBasedProvider(
            MovingAverageAnalysisService movingAverageAnalysisService,
            MovingAverageOrderDecisionContextFactory decisionContextFactory,
            MovingAverageOrderDecisionProvider decisionProvider,
            MovingAverageOrderDecisionResolver decisionResolver,
            SwingV1DecisionService swingDecisionService,
            MarketTradingDayPolicy marketTradingDayPolicy,
            Clock clock
    ) {
        InvestmentAgent defaultProvider = new InvestmentAgent(
                movingAverageAnalysisService,
                decisionContextFactory,
                decisionProvider,
                decisionResolver
        );
        SwingV1AgentNextActionProvider swingV1Provider =
                new SwingV1AgentNextActionProvider(
                        swingDecisionService,
                        marketTradingDayPolicy,
                        clock
                );
        return new StrategyRuleBasedAgentNextActionProvider(
                defaultProvider,
                swingV1Provider
        );
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
