package com.stock.agent.decision.movingaverage.provider.ai.openai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(
        prefix = "agent.decision.moving-average.ai.openai"
)
public record OpenAiMovingAverageOrderDecisionProperties(
        URI baseUrl,
        String apiKey,
        String model,
        OpenAiReasoningEffort reasoningEffort,
        Duration requestTimeout,
        int maxOutputTokens
) {
    public OpenAiMovingAverageOrderDecisionProperties {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null.");
        apiKey = apiKey == null ? "" : apiKey;
        model = requireText(model, "model");
        Objects.requireNonNull(
                reasoningEffort,
                "reasoningEffort must not be null."
        );
        if (requestTimeout == null
                || requestTimeout.isZero()
                || requestTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "requestTimeout must be positive."
            );
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException(
                    "maxOutputTokens must be positive."
            );
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }
}
