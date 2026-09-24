package com.stock.runtime.profile;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PaperObservationProfileConfigurationTest {

    @Test
    void enablesObservationWithoutBrokerOrderExecution() throws IOException {
        ConfigurableEnvironment environment = loadEnvironment();

        assertThat(environment.getProperty("broker.kis.enabled", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("broker.kis.base-url"))
                .isEqualTo("https://openapivts.koreainvestment.com:29443");
        assertThat(environment.getProperty("harness.scheduler.enabled", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("trade.execution.mode"))
                .isEqualTo("VIRTUAL");
        assertThat(environment.getProperty(
                "broker.order.reconciliation.scheduler.enabled",
                Boolean.class
        )).isFalse();
        assertThat(environment.getProperty(
                "broker.order.cancellation.scheduler.enabled",
                Boolean.class
        )).isFalse();
    }

    private ConfigurableEnvironment loadEnvironment() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

        loader.load(
                "application.yml",
                new ClassPathResource("application.yml")
        ).forEach(environment.getPropertySources()::addLast);
        loader.load(
                "application-paper-observation.yml",
                new ClassPathResource("application-paper-observation.yml")
        ).forEach(environment.getPropertySources()::addFirst);

        return environment;
    }
}
