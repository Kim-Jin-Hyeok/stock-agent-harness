package com.stock.market.calendar;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MarketCalendarPropertiesYamlBindingTest {

    @Test
    void binds2026MarketClosedDatesFromApplicationYaml() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        MarketCalendarProperties properties = Binder.get(environment)
                .bind("market.calendar", Bindable.of(MarketCalendarProperties.class))
                .orElseThrow(() -> new IllegalStateException("market.calendar must be configured."));

        assertThat(properties.closedDates()).containsExactlyInAnyOrder(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 16),
                LocalDate.of(2026, 2, 17),
                LocalDate.of(2026, 2, 18),
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 5),
                LocalDate.of(2026, 5, 25),
                LocalDate.of(2026, 6, 3),
                LocalDate.of(2026, 7, 17),
                LocalDate.of(2026, 8, 17),
                LocalDate.of(2026, 9, 24),
                LocalDate.of(2026, 9, 25),
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 12, 25),
                LocalDate.of(2026, 12, 31)
        );
    }
}
