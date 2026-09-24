package com.stock.runtime.profile;

import com.stock.broker.kis.config.KisProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

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

    @Test
    void bindsPaperCredentialsInsteadOfGenericCredentials() throws IOException {
        ConfigurableEnvironment environment = loadEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "test-kis-credentials",
                Map.of(
                        "KIS_APP_KEY", "generic-app-key",
                        "KIS_APP_SECRET", "generic-app-secret",
                        "KIS_ACCOUNT_NUMBER", "generic-account-number",
                        "KIS_ACCOUNT_PRODUCT_CODE", "generic-product-code",
                        "KIS_PAPER_APP_KEY", "paper-app-key",
                        "KIS_PAPER_APP_SECRET", "paper-app-secret",
                        "KIS_PAPER_ACCOUNT_NUMBER", "paper-account-number",
                        "KIS_PAPER_ACCOUNT_PRODUCT_CODE", "paper-product-code"
                )
        ));

        KisProperties properties = Binder.get(environment)
                .bind("broker.kis", Bindable.of(KisProperties.class))
                .orElseThrow(() -> new AssertionError(
                        "KIS properties were not bound."
                ));

        assertThat(properties.appKey()).isEqualTo("paper-app-key");
        assertThat(properties.appSecret()).isEqualTo("paper-app-secret");
        assertThat(properties.accountNumber()).isEqualTo("paper-account-number");
        assertThat(properties.accountProductCode()).isEqualTo("paper-product-code");
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
