package com.stock.agent.provider.ai.openai.config;

import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContextFactory;
import com.stock.agent.decision.movingaverage.provider.MovingAverageOrderDecisionProvider;
import com.stock.agent.decision.movingaverage.resolution.MovingAverageOrderDecisionResolver;
import com.stock.agent.decision.swing.v1.SwingV1DecisionService;
import com.stock.agent.provider.AgentNextActionProvider;
import com.stock.agent.provider.ai.AiAgentNextActionProvider;
import com.stock.agent.provider.ai.execution.AgentNextActionAiExecutor;
import com.stock.agent.provider.ai.openai.client.OpenAiResponsesClient;
import com.stock.agent.provider.ai.openai.client.request.OpenAiAgentNextActionRequestFactory;
import com.stock.agent.provider.ai.openai.client.response.OpenAiAgentNextActionResponseInterpreter;
import com.stock.agent.provider.ai.openai.execution.OpenAiAgentNextActionExecutor;
import com.stock.agent.provider.ai.openai.response.OpenAiAgentNextActionResponseMapper;
import com.stock.agent.provider.ai.prompt.AgentNextActionAiPromptFactory;
import com.stock.agent.provider.ai.request.AgentNextActionAiRequestFactory;
import com.stock.agent.provider.config.AgentNextActionProviderConfiguration;
import com.stock.agent.provider.config.AgentNextActionProviderProperties;
import com.stock.agent.provider.config.AgentNextActionProviderType;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenAiAgentNextActionConfigurationTest {
    private static final String OPENAI_ENABLED_PROPERTY =
            "agent.provider.ai.openai.enabled=true";
    private static final String OPENAI_DISABLED_PROPERTY =
            "agent.provider.ai.openai.enabled=false";
    private static final String AI_PROVIDER_PROPERTY =
            "agent.next-action.provider-type=AI";
    private static final String RULE_BASED_PROVIDER_PROPERTY =
            "agent.next-action.provider-type=RULE_BASED";

    @Test
    void doesNotCreateAgentExecutorForRuleBasedProvider() {
        contextRunner()
                .withPropertyValues(
                        OPENAI_ENABLED_PROPERTY,
                        RULE_BASED_PROVIDER_PROPERTY
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            OpenAiResponsesClient.class
                    );
                    assertThat(context).doesNotHaveBean(
                            AgentNextActionAiExecutor.class
                    );
                });
    }

    @Test
    void createsOpenAiAgentExecutorForAiProvider() {
        contextRunner()
                .withPropertyValues(
                        OPENAI_ENABLED_PROPERTY,
                        AI_PROVIDER_PROPERTY
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            OpenAiResponsesClient.class
                    );
                    assertThat(context).hasSingleBean(
                            AgentNextActionAiExecutor.class
                    );
                    assertThat(context.getBean(
                            AgentNextActionAiExecutor.class
                    )).isInstanceOf(
                            OpenAiAgentNextActionExecutor.class
                    );
                });
    }

    @Test
    void aiProviderFailsWhenOpenAiIsDisabled() {
        contextRunner()
                .withPropertyValues(
                        OPENAI_DISABLED_PROPERTY,
                        AI_PROVIDER_PROPERTY
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(
                                    NoSuchBeanDefinitionException.class
                            );
                });
    }

    @Test
    void createsAiAgentNextActionProviderWithOpenAiExecutor() {
        contextRunner()
                .withUserConfiguration(
                        AgentNextActionProviderConfiguration.class
                )
                .withPropertyValues(
                        OPENAI_ENABLED_PROPERTY,
                        AI_PROVIDER_PROPERTY
                )
                .withBean(
                        AgentNextActionProviderProperties.class,
                        () -> new AgentNextActionProviderProperties(
                                AgentNextActionProviderType.AI
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
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(
                        AgentNextActionAiRequestFactory.class,
                        AgentNextActionAiRequestFactory::new
                )
                .withBean(
                        AgentNextActionAiPromptFactory.class,
                        AgentNextActionAiPromptFactory::new
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(
                            AgentNextActionProvider.class
                    );
                    assertThat(context.getBean(
                            AgentNextActionProvider.class
                    )).isInstanceOf(AiAgentNextActionProvider.class);
                });
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(
                        OpenAiProviderConfiguration.class,
                        OpenAiAgentNextActionConfiguration.class
                )
                .withBean(
                        OpenAiProviderProperties.class,
                        this::properties
                )
                .withBean(
                        OpenAiAgentNextActionRequestFactory.class,
                        () -> mock(
                                OpenAiAgentNextActionRequestFactory.class
                        )
                )
                .withBean(
                        OpenAiAgentNextActionResponseInterpreter.class,
                        () -> mock(
                                OpenAiAgentNextActionResponseInterpreter.class
                        )
                )
                .withBean(
                        OpenAiAgentNextActionResponseMapper.class,
                        () -> mock(
                                OpenAiAgentNextActionResponseMapper.class
                        )
                );
    }

    private OpenAiProviderProperties properties() {
        return new OpenAiProviderProperties(
                URI.create("https://api.openai.com"),
                "test-api-key",
                "gpt-6-luna",
                OpenAiReasoningEffort.LOW,
                Duration.ofSeconds(15),
                512
        );
    }
}
