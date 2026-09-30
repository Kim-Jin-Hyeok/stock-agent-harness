package com.stock.market.index.history.collection.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class MarketIndexDailyHistoryCollectionPropertiesYamlBindingTest {

    @Test
    void bindsCollectionSettingsFromApplicationYaml() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load(
                        "application.yml",
                        new ClassPathResource("application.yml")
                )
                .forEach(environment.getPropertySources()::addLast);

        MarketIndexDailyHistoryCollectionProperties properties =
                Binder.get(environment)
                        .bind(
                                "market.index.history.collection",
                                Bindable.of(
                                        MarketIndexDailyHistoryCollectionProperties.class
                                )
                        )
                        .orElseThrow(() -> new IllegalStateException(
                                "Market index daily history collection must "
                                        + "be configured."
                        ));

        assertThat(properties.dailyBarAvailableAt())
                .isEqualTo(LocalTime.of(20, 10));
        assertThat(properties.benchmarkIds()).containsExactly("KOSPI");
        assertThat(properties.initialLookbackYears()).isEqualTo(3);
    }
}
