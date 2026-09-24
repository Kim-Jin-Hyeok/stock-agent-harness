package com.stock.market.price.history.collection.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class DailyPriceHistoryCollectionPropertiesYamlBindingTest {

    @Test
    void bindsDailyBarAvailableAtFromApplicationYaml() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load(
                        "application.yml",
                        new ClassPathResource("application.yml")
                )
                .forEach(environment.getPropertySources()::addLast);

        DailyPriceHistoryCollectionProperties properties =
                Binder.get(environment)
                        .bind(
                                "market.price.history.collection",
                                Bindable.of(
                                        DailyPriceHistoryCollectionProperties.class
                                )
                        )
                        .orElseThrow(() -> new IllegalStateException(
                                "Daily price history collection must be configured."
                        ));

        assertThat(properties.dailyBarAvailableAt())
                .isEqualTo(LocalTime.of(20, 10));
    }
}
