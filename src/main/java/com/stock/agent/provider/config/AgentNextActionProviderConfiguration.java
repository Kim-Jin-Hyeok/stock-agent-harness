package com.stock.agent.provider.config;

import com.stock.agent.InvestmentAgent;
import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContextFactory;
import com.stock.agent.decision.movingaverage.provider.MovingAverageOrderDecisionProvider;
import com.stock.agent.decision.movingaverage.resolution.MovingAverageOrderDecisionResolver;
import com.stock.agent.decision.swing.v1.policy.SwingV1ActionPolicy;
import com.stock.agent.decision.swing.v1.quantity.policy.SwingV1OrderQuantityPolicy;
import com.stock.agent.decision.swing.v1.resolution.SwingV1DecisionResolver;
import com.stock.agent.provider.AgentNextActionProvider;
import com.stock.agent.provider.ai.AiAgentNextActionProvider;
import com.stock.agent.provider.ai.execution.AgentNextActionAiExecutor;
import com.stock.agent.provider.ai.prompt.AgentNextActionAiPromptFactory;
import com.stock.agent.provider.ai.request.AgentNextActionAiRequestFactory;
import com.stock.agent.provider.rulebased.StrategyRuleBasedAgentNextActionProvider;
import com.stock.agent.provider.rulebased.swing.v1.SwingV1AgentNextActionProvider;
import com.stock.portfolio.valuation.PortfolioValuationService;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisService;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisService;
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
            SwingTechnicalAnalysisService swingAnalysisService,
            SwingV1ActionPolicy swingActionPolicy,
            PortfolioValuationService portfolioValuationService,
            SwingV1OrderQuantityPolicy swingOrderQuantityPolicy,
            SwingV1DecisionResolver swingDecisionResolver,
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
                    swingAnalysisService,
                    swingActionPolicy,
                    portfolioValuationService,
                    swingOrderQuantityPolicy,
                    swingDecisionResolver,
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
            SwingTechnicalAnalysisService swingAnalysisService,
            SwingV1ActionPolicy swingActionPolicy,
            PortfolioValuationService portfolioValuationService,
            SwingV1OrderQuantityPolicy swingOrderQuantityPolicy,
            SwingV1DecisionResolver swingDecisionResolver,
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
                        swingAnalysisService,
                        swingActionPolicy,
                        portfolioValuationService,
                        swingOrderQuantityPolicy,
                        swingDecisionResolver,
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
