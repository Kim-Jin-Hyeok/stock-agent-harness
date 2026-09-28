package com.stock.agent.decision.movingaverage.provider.config;

import com.stock.agent.decision.movingaverage.provider.MovingAverageOrderDecisionProvider;
import com.stock.agent.decision.movingaverage.provider.ai.AiMovingAverageOrderDecisionProvider;
import com.stock.agent.decision.movingaverage.provider.ai.execution.MovingAverageOrderDecisionAiExecutor;
import com.stock.agent.decision.movingaverage.provider.ai.prompt.MovingAverageOrderDecisionAiPromptFactory;
import com.stock.agent.decision.movingaverage.provider.ai.request.MovingAverageOrderDecisionAiRequestFactory;
import com.stock.agent.decision.movingaverage.provider.rulebased.MaxCapacityMovingAverageOrderDecisionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MovingAverageOrderDecisionConfigurationTest {

    @Test
    void createsRuleBasedProviderForRuleBasedType() {
        contextRunner(MovingAverageOrderDecisionProviderType.RULE_BASED)
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            MovingAverageOrderDecisionProvider.class
                    );
                    assertThat(context.getBean(
                            MovingAverageOrderDecisionProvider.class
                    )).isInstanceOf(
                            MaxCapacityMovingAverageOrderDecisionProvider.class
                    );
                });
    }

    @Test
    void createsAiProviderForAiType() {
        contextRunner(MovingAverageOrderDecisionProviderType.AI)
                .withBean(
                        MovingAverageOrderDecisionAiExecutor.class,
                        () -> mock(
                                MovingAverageOrderDecisionAiExecutor.class
                        )
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            MovingAverageOrderDecisionProvider.class
                    );
                    assertThat(context.getBean(
                            MovingAverageOrderDecisionProvider.class
                    )).isInstanceOf(
                            AiMovingAverageOrderDecisionProvider.class
                    );
                });
    }

    @Test
    void aiTypeFailsWithoutAiExecutor() {
        contextRunner(MovingAverageOrderDecisionProviderType.AI)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "MovingAverageOrderDecisionAiExecutor "
                                            + "must be configured when "
                                            + "provider type is AI."
                            );
                });
    }

    private ApplicationContextRunner contextRunner(
            MovingAverageOrderDecisionProviderType providerType
    ) {
        return new ApplicationContextRunner()
                .withUserConfiguration(
                        MovingAverageOrderDecisionConfiguration.class
                )
                .withBean(
                        MovingAverageOrderDecisionProperties.class,
                        () -> new MovingAverageOrderDecisionProperties(
                                providerType
                        )
                )
                .withBean(
                        MovingAverageOrderDecisionAiRequestFactory.class,
                        MovingAverageOrderDecisionAiRequestFactory::new
                )
                .withBean(
                        MovingAverageOrderDecisionAiPromptFactory.class,
                        MovingAverageOrderDecisionAiPromptFactory::new
                );
    }
}
