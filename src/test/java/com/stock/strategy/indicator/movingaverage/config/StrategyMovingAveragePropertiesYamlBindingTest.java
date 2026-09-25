package com.stock.strategy.indicator.movingaverage.config;

import com.stock.strategy.profile.InvestmentHorizon;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class StrategyMovingAveragePropertiesYamlBindingTest {

    @Test
    void bindsStrategyMovingAveragePeriodsFromApplicationYaml()
            throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load(
                        "application.yml",
                        new ClassPathResource("application.yml")
                )
                .forEach(environment.getPropertySources()::addLast);

        StrategyMovingAverageProperties properties = Binder.get(environment)
                .bind(
                        "strategy.moving-average",
                        Bindable.of(StrategyMovingAverageProperties.class)
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Strategy moving average periods must be configured."
                ));

        assertThat(properties.periods()).hasSize(3);
        assertThat(properties.periods())
                .extracting(
                        ConfiguredStrategyMovingAveragePeriods::horizon,
                        ConfiguredStrategyMovingAveragePeriods::shortPeriod,
                        ConfiguredStrategyMovingAveragePeriods::longPeriod
                )
                .containsExactly(
                        tuple(InvestmentHorizon.DAY_TRADING, 5, 20),
                        tuple(InvestmentHorizon.SWING, 20, 60),
                        tuple(InvestmentHorizon.LONG_TERM, 50, 200)
                );
    }
}
