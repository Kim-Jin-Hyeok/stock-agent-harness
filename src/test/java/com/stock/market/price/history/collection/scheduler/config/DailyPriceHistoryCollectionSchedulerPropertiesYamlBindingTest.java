package com.stock.market.price.history.collection.scheduler.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class DailyPriceHistoryCollectionSchedulerPropertiesYamlBindingTest {

    @Test
    void bindsSchedulerSettingsFromApplicationYaml() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load(
                        "application.yml",
                        new ClassPathResource("application.yml")
                )
                .forEach(environment.getPropertySources()::addLast);

        DailyPriceHistoryCollectionSchedulerProperties properties =
                Binder.get(environment)
                        .bind(
                                "market.price.history.collection.scheduler",
                                Bindable.of(
                                        DailyPriceHistoryCollectionSchedulerProperties.class
                                )
                        )
                        .orElseThrow(() -> new IllegalStateException(
                                "Daily price history collection scheduler "
                                        + "must be configured."
                        ));

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.cron()).isEqualTo("0 15 20 * * MON-FRI");
        assertThat(properties.schedulerZoneId())
                .isEqualTo(ZoneId.of("Asia/Seoul"));
    }
}
