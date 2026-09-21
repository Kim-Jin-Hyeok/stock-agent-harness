package com.stock.market.calendar;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class MarketCalendarPropertiesYamlBindingTest {

    @Test
    void bindsEmptyClosedDatesFromApplicationYaml() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        MarketCalendarProperties properties = Binder.get(environment)
                .bind("market.calendar", Bindable.of(MarketCalendarProperties.class))
                .orElseThrow(() -> new IllegalStateException("market.calendar must be configured."));

        assertThat(properties.closedDates()).isEmpty();
    }
}
