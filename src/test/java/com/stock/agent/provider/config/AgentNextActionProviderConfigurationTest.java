package com.stock.agent.provider.config;

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
import com.stock.market.calendar.MarketTradingDayPolicy;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisService;
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
                        SwingV1DecisionService.class,
                        () -> mock(SwingV1DecisionService.class)
                )
                .withBean(
                        MarketTradingDayPolicy.class,
                        () -> mock(MarketTradingDayPolicy.class)
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
