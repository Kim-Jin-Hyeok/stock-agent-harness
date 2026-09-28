package com.stock.agent.decision.movingaverage.provider.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class MovingAverageOrderDecisionPropertiesYamlBindingTest {

    @Test
    void bindsRuleBasedProviderTypeFromApplicationYaml() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        new YamlPropertySourceLoader()
                .load(
                        "application.yml",
                        new ClassPathResource("application.yml")
                )
                .forEach(environment.getPropertySources()::addLast);

        MovingAverageOrderDecisionProperties properties =
                Binder.get(environment)
                        .bind(
                                "agent.decision.moving-average",
                                Bindable.of(
                                        MovingAverageOrderDecisionProperties.class
                                )
                        )
                        .orElseThrow(() -> new IllegalStateException(
                                "Moving average order decision provider must "
                                        + "be configured."
                        ));

        assertThat(properties.providerType())
                .isEqualTo(
                        MovingAverageOrderDecisionProviderType.RULE_BASED
                );
    }
}
