package com.stock.agent.provider.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class AgentNextActionProviderPropertiesYamlBindingTest {

    @Test
    void bindsRuleBasedProviderTypeFromApplicationYaml() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        new YamlPropertySourceLoader()
                .load(
                        "application.yml",
                        new ClassPathResource("application.yml")
                )
                .forEach(environment.getPropertySources()::addLast);

        AgentNextActionProviderProperties properties =
                Binder.get(environment)
                        .bind(
                                "agent.next-action",
                                Bindable.of(
                                        AgentNextActionProviderProperties.class
                                )
                        )
                        .orElseThrow(() -> new IllegalStateException(
                                "Agent next action provider must be "
                                        + "configured."
                        ));

        assertThat(properties.providerType())
                .isEqualTo(AgentNextActionProviderType.RULE_BASED);
    }
}
