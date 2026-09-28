package com.stock.agent.provider.ai.openai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiProviderPropertiesYamlBindingTest {

    @Test
    void bindsDefaultOpenAiSettingsFromApplicationYaml() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        new YamlPropertySourceLoader()
                .load(
                        "application.yml",
                        new ClassPathResource("application.yml")
                )
                .forEach(environment.getPropertySources()::addLast);

        OpenAiProviderProperties properties = Binder.get(environment)
                .bind(
                        "agent.provider.ai.openai",
                        Bindable.of(OpenAiProviderProperties.class)
                )
                .orElseThrow(() -> new IllegalStateException(
                        "OpenAI provider settings must be configured."
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
