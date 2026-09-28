package com.stock.agent.decision.movingaverage.provider.ai.openai.config;

import com.stock.agent.decision.movingaverage.provider.MovingAverageOrderDecisionProvider;
import com.stock.agent.decision.movingaverage.provider.ai.AiMovingAverageOrderDecisionProvider;
import com.stock.agent.decision.movingaverage.provider.ai.execution.MovingAverageOrderDecisionAiExecutor;
import com.stock.agent.decision.movingaverage.provider.ai.openai.client.request.OpenAiResponsesRequestFactory;
import com.stock.agent.decision.movingaverage.provider.ai.openai.client.response.OpenAiResponsesResponseInterpreter;
import com.stock.agent.decision.movingaverage.provider.ai.openai.execution.OpenAiMovingAverageOrderDecisionExecutor;
import com.stock.agent.decision.movingaverage.provider.ai.openai.response.OpenAiMovingAverageOrderDecisionResponseMapper;
import com.stock.agent.decision.movingaverage.provider.ai.prompt.MovingAverageOrderDecisionAiPromptFactory;
import com.stock.agent.decision.movingaverage.provider.ai.request.MovingAverageOrderDecisionAiRequestFactory;
import com.stock.agent.decision.movingaverage.provider.config.MovingAverageOrderDecisionConfiguration;
import com.stock.agent.decision.movingaverage.provider.config.MovingAverageOrderDecisionProperties;
import com.stock.agent.decision.movingaverage.provider.config.MovingAverageOrderDecisionProviderType;
import com.stock.agent.provider.ai.openai.client.OpenAiResponsesClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenAiMovingAverageOrderDecisionConfigurationTest {
    private static final String AI_PROVIDER_PROPERTY =
            "agent.decision.moving-average.provider-type=AI";
    private static final String RULE_BASED_PROVIDER_PROPERTY =
            "agent.decision.moving-average.provider-type=RULE_BASED";

    @Test
    void doesNotCreateOpenAiBeansForRuleBasedProvider() {
        contextRunner("test-api-key")
                .withPropertyValues(RULE_BASED_PROVIDER_PROPERTY)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RestClient.class);
                    assertThat(context).doesNotHaveBean(
                            OpenAiResponsesClient.class
                    );
                    assertThat(context).doesNotHaveBean(
                            MovingAverageOrderDecisionAiExecutor.class
                    );
                });
    }

    @Test
    void createsOpenAiBeansForAiProvider() {
        contextRunner("test-api-key")
                .withPropertyValues(AI_PROVIDER_PROPERTY)
                .run(context -> {
                    assertThat(context).hasSingleBean(RestClient.class);
                    assertThat(context).hasSingleBean(
                            OpenAiResponsesClient.class
                    );
                    assertThat(context).hasSingleBean(
                            MovingAverageOrderDecisionAiExecutor.class
                    );
                    assertThat(context.getBean(
                            MovingAverageOrderDecisionAiExecutor.class
                    )).isInstanceOf(
                            OpenAiMovingAverageOrderDecisionExecutor.class
                    );
                });
    }

    @Test
    void aiProviderFailsWithBlankApiKey() {
        contextRunner(" ")
                .withPropertyValues(AI_PROVIDER_PROPERTY)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "apiKey must not be blank."
                            );
                });
    }

    @Test
    void createsAiDecisionProviderWithOpenAiExecutor() {
        contextRunner("test-api-key")
                .withUserConfiguration(
                        MovingAverageOrderDecisionConfiguration.class
                )
                .withBean(
                        MovingAverageOrderDecisionProperties.class,
                        () -> new MovingAverageOrderDecisionProperties(
                                MovingAverageOrderDecisionProviderType.AI
                        )
                )
                .withBean(
                        MovingAverageOrderDecisionAiRequestFactory.class,
                        MovingAverageOrderDecisionAiRequestFactory::new
                )
                .withBean(
                        MovingAverageOrderDecisionAiPromptFactory.class,
                        MovingAverageOrderDecisionAiPromptFactory::new
                )
                .withPropertyValues(AI_PROVIDER_PROPERTY)
                .run(context -> {
                    assertThat(context).hasNotFailed();
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

    private ApplicationContextRunner contextRunner(String apiKey) {
        return new ApplicationContextRunner()
                .withUserConfiguration(
                        OpenAiMovingAverageOrderDecisionConfiguration.class
                )
                .withBean(
                        OpenAiMovingAverageOrderDecisionProperties.class,
                        () -> properties(apiKey)
                )
                .withBean(
                        OpenAiResponsesRequestFactory.class,
                        () -> mock(OpenAiResponsesRequestFactory.class)
                )
                .withBean(
                        OpenAiResponsesResponseInterpreter.class,
                        () -> mock(OpenAiResponsesResponseInterpreter.class)
                )
                .withBean(
                        OpenAiMovingAverageOrderDecisionResponseMapper.class,
                        () -> mock(
                                OpenAiMovingAverageOrderDecisionResponseMapper.class
                        )
                );
    }

    private OpenAiMovingAverageOrderDecisionProperties properties(
            String apiKey
    ) {
        return new OpenAiMovingAverageOrderDecisionProperties(
                URI.create("https://api.openai.com"),
                apiKey,
                "gpt-6-luna",
                OpenAiReasoningEffort.LOW,
                Duration.ofSeconds(15),
                512
        );
    }
}
