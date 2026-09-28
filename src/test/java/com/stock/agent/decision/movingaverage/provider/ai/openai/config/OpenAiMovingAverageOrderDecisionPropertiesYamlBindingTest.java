package com.stock.agent.decision.movingaverage.provider.ai.openai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiMovingAverageOrderDecisionPropertiesYamlBindingTest {

    @Test
    void bindsDefaultOpenAiSettingsFromApplicationYaml() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        new YamlPropertySourceLoader()
                .load(
                        "application.yml",
                        new ClassPathResource("application.yml")
                )
                .forEach(environment.getPropertySources()::addLast);

        OpenAiMovingAverageOrderDecisionProperties properties =
                Binder.get(environment)
                        .bind(
                                "agent.decision.moving-average.ai.openai",
                                Bindable.of(
                                        OpenAiMovingAverageOrderDecisionProperties.class
                                )
                        )
                        .orElseThrow(() -> new IllegalStateException(
                                "OpenAI moving average order decision settings "
                                        + "must be configured."
                        ));

        assertThat(properties.baseUrl())
                .isEqualTo(URI.create("https://api.openai.com"));
        assertThat(properties.apiKey()).isEmpty();
        assertThat(properties.model()).isEqualTo("gpt-6-luna");
        assertThat(properties.reasoningEffort())
                .isEqualTo(OpenAiReasoningEffort.LOW);
        assertThat(properties.requestTimeout())
                .isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.maxOutputTokens()).isEqualTo(512);
    }
}
