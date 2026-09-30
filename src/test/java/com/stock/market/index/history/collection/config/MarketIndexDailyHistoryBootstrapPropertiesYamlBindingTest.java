package com.stock.market.index.history.collection.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class MarketIndexDailyHistoryBootstrapPropertiesYamlBindingTest {

    @Test
    void bindsBootstrapSettingsFromApplicationYaml() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load(
                        "application.yml",
                        new ClassPathResource("application.yml")
                )
                .forEach(environment.getPropertySources()::addLast);

        MarketIndexDailyHistoryBootstrapProperties properties =
                Binder.get(environment)
                        .bind(
                                "market.index.history.collection.bootstrap",
                                Bindable.of(
                                        MarketIndexDailyHistoryBootstrapProperties.class
                                )
                        )
                        .orElseThrow(() -> new IllegalStateException(
                                "Market index daily history bootstrap must "
                                        + "be configured."
                        ));

        assertThat(properties.enabled()).isFalse();
    }
}
