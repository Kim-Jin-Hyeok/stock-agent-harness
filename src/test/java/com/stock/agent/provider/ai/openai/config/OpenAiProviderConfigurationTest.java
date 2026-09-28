package com.stock.agent.provider.ai.openai.config;

import com.stock.agent.provider.ai.openai.client.OpenAiResponsesClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiProviderConfigurationTest {
    private static final String ENABLED_PROPERTY =
            "agent.provider.ai.openai.enabled=true";
    private static final String DISABLED_PROPERTY =
            "agent.provider.ai.openai.enabled=false";

    @Test
    void doesNotCreateOpenAiClientWhenDisabled() {
        contextRunner("test-api-key")
                .withPropertyValues(DISABLED_PROPERTY)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RestClient.class);
                    assertThat(context).doesNotHaveBean(
                            OpenAiResponsesClient.class
                    );
                });
    }

    @Test
    void createsSingleOpenAiClientWhenEnabled() {
        contextRunner("test-api-key")
                .withPropertyValues(ENABLED_PROPERTY)
                .run(context -> {
                    assertThat(context).hasSingleBean(RestClient.class);
                    assertThat(context).hasSingleBean(
                            OpenAiResponsesClient.class
                    );
                });
    }

    @Test
    void enabledProviderFailsWithBlankApiKey() {
        contextRunner(" ")
                .withPropertyValues(ENABLED_PROPERTY)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "apiKey must not be blank."
                            );
                });
    }

    private ApplicationContextRunner contextRunner(String apiKey) {
        return new ApplicationContextRunner()
                .withUserConfiguration(OpenAiProviderConfiguration.class)
                .withBean(
                        OpenAiProviderProperties.class,
                        () -> properties(apiKey)
                );
    }

    private OpenAiProviderProperties properties(String apiKey) {
        return new OpenAiProviderProperties(
                URI.create("https://api.openai.com"),
                apiKey,
                "gpt-6-luna",
                OpenAiReasoningEffort.LOW,
                Duration.ofSeconds(15),
                512
        );
    }
}
