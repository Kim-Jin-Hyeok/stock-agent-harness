package com.stock.harness.scheduler.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessSchedulerPropertiesYamlBindingTest {

    @Test
    void bindsStrategyRunWindowsFromApplicationYaml() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        HarnessSchedulerProperties properties = Binder.get(environment)
                .bind("harness.scheduler", Bindable.of(HarnessSchedulerProperties.class))
                .orElseThrow(() -> new IllegalStateException("harness.scheduler must be configured."));

        assertThat(properties.strategies()).hasSize(3);
        assertThat(properties.strategies().get(0).runWindow().allowedDays())
                .containsExactly(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY
                );
        assertThat(properties.strategies().get(0).runWindow().startsAt())
                .isEqualTo(LocalTime.of(9, 0));
        assertThat(properties.strategies().get(1).runWindow().startsAt())
                .isEqualTo(LocalTime.of(15, 0));
        assertThat(properties.strategies().get(2).runWindow().allowedDays())
                .containsExactly(DayOfWeek.FRIDAY);
        assertThat(properties.strategies().get(2).runWindow().endsAt())
                .isEqualTo(LocalTime.of(15, 30));
    }
}
