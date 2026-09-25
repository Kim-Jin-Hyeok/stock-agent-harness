package com.stock.market.price.history.collection.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class DailyPriceHistoryBootstrapPropertiesYamlBindingTest {

    @Test
    void bindsBootstrapSettingsFromApplicationYaml() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load(
                        "application.yml",
                        new ClassPathResource("application.yml")
                )
                .forEach(environment.getPropertySources()::addLast);

        DailyPriceHistoryBootstrapProperties properties =
                Binder.get(environment)
                        .bind(
                                "market.price.history.collection.bootstrap",
                                Bindable.of(
                                        DailyPriceHistoryBootstrapProperties.class
                                )
                        )
                        .orElseThrow(() -> new IllegalStateException(
                                "Daily price history bootstrap must be configured."
                        ));

        assertThat(properties.enabled()).isFalse();
    }
}
