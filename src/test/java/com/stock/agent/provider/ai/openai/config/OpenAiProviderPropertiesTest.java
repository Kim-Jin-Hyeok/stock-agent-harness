package com.stock.agent.provider.ai.openai.config;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OpenAiProviderPropertiesTest {
    private static final URI BASE_URL = URI.create("https://api.openai.com");

    @Test
    void allowsMissingApiKeyUntilAiExecutorIsConfigured() {
        OpenAiProviderProperties properties = properties(
                null,
                "gpt-6-luna",
                OpenAiReasoningEffort.LOW,
                Duration.ofSeconds(15),
                512
        );

        assertThat(properties.apiKey()).isEmpty();
    }

    @Test
    void rejectsNullBaseUrl() {
        assertThatNullPointerException()
                .isThrownBy(() -> new OpenAiProviderProperties(
                        null,
                        "",
                        "gpt-6-luna",
                        OpenAiReasoningEffort.LOW,
                        Duration.ofSeconds(15),
                        512
                ))
                .withMessage("baseUrl must not be null.");
    }

    @Test
    void rejectsBlankModel() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(
                        "",
                        " ",
                        OpenAiReasoningEffort.LOW,
                        Duration.ofSeconds(15),
                        512
                ))
                .withMessage("model must not be blank.");
    }

    @Test
    void rejectsNullReasoningEffort() {
        assertThatNullPointerException()
                .isThrownBy(() -> properties(
                        "",
                        "gpt-6-luna",
                        null,
                        Duration.ofSeconds(15),
                        512
                ))
                .withMessage("reasoningEffort must not be null.");
    }

    @Test
    void rejectsNonPositiveRequestTimeout() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(
                        "",
                        "gpt-6-luna",
                        OpenAiReasoningEffort.LOW,
                        Duration.ZERO,
                        512
                ))
                .withMessage("requestTimeout must be positive.");
    }

    @Test
    void rejectsNonPositiveMaxOutputTokens() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(
                        "",
                        "gpt-6-luna",
                        OpenAiReasoningEffort.LOW,
                        Duration.ofSeconds(15),
                        0
                ))
                .withMessage("maxOutputTokens must be positive.");
    }

    private OpenAiProviderProperties properties(
            String apiKey,
            String model,
            OpenAiReasoningEffort reasoningEffort,
            Duration requestTimeout,
            int maxOutputTokens
    ) {
        return new OpenAiProviderProperties(
                BASE_URL,
                apiKey,
                model,
                reasoningEffort,
                requestTimeout,
                maxOutputTokens
        );
    }
}
