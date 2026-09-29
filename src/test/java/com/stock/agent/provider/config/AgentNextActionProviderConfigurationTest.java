package com.stock.agent.provider.config;

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
import com.stock.portfolio.valuation.PortfolioValuationService;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisService;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentNextActionProviderConfigurationTest {

    @Test
    void createsRuleBasedProviderForRuleBasedType() {
        contextRunner(AgentNextActionProviderType.RULE_BASED)
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            AgentNextActionProvider.class
                    );
                    assertThat(context.getBean(
                            AgentNextActionProvider.class
                    )).isInstanceOf(
                            StrategyRuleBasedAgentNextActionProvider.class
                    );
                });
    }

    @Test
    void createsAiProviderForAiType() {
        contextRunner(AgentNextActionProviderType.AI)
                .withBean(
                        AgentNextActionAiExecutor.class,
                        () -> mock(AgentNextActionAiExecutor.class)
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            AgentNextActionProvider.class
                    );
                    assertThat(context.getBean(
                            AgentNextActionProvider.class
                    )).isInstanceOf(AiAgentNextActionProvider.class);
                });
    }

    @Test
    void aiTypeFailsWithoutAiExecutor() {
        contextRunner(AgentNextActionProviderType.AI)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "AgentNextActionAiExecutor must be "
                                            + "configured when provider type "
                                            + "is AI."
                            );
                });
    }

    private ApplicationContextRunner contextRunner(
            AgentNextActionProviderType providerType
    ) {
        return new ApplicationContextRunner()
                .withUserConfiguration(
                        AgentNextActionProviderConfiguration.class
                )
                .withBean(
                        AgentNextActionProviderProperties.class,
                        () -> new AgentNextActionProviderProperties(
                                providerType
                        )
                )
                .withBean(
                        MovingAverageAnalysisService.class,
                        () -> mock(MovingAverageAnalysisService.class)
                )
                .withBean(
                        MovingAverageOrderDecisionContextFactory.class,
                        () -> mock(
                                MovingAverageOrderDecisionContextFactory.class
                        )
                )
                .withBean(
                        MovingAverageOrderDecisionProvider.class,
                        () -> mock(MovingAverageOrderDecisionProvider.class)
                )
                .withBean(
                        MovingAverageOrderDecisionResolver.class,
                        () -> mock(MovingAverageOrderDecisionResolver.class)
                )
                .withBean(
                        SwingTechnicalAnalysisService.class,
                        () -> mock(SwingTechnicalAnalysisService.class)
                )
                .withBean(
                        SwingV1ActionPolicy.class,
                        () -> mock(SwingV1ActionPolicy.class)
                )
                .withBean(
                        PortfolioValuationService.class,
                        () -> mock(PortfolioValuationService.class)
                )
                .withBean(
                        SwingV1OrderQuantityPolicy.class,
                        () -> mock(SwingV1OrderQuantityPolicy.class)
                )
                .withBean(
                        SwingV1DecisionResolver.class,
                        () -> mock(SwingV1DecisionResolver.class)
                )
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(
                        AgentNextActionAiRequestFactory.class,
                        AgentNextActionAiRequestFactory::new
                )
                .withBean(
                        AgentNextActionAiPromptFactory.class,
                        AgentNextActionAiPromptFactory::new
                );
    }
}
