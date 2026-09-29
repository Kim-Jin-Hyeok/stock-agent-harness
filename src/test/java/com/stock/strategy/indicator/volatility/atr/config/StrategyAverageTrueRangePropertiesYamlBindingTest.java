package com.stock.strategy.indicator.volatility.atr.config;

import com.stock.strategy.profile.InvestmentHorizon;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyAverageTrueRangePropertiesYamlBindingTest {

    @Test
    void bindsStrategyAverageTrueRangePeriodsFromApplicationYaml()
            throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load(
                        "application.yml",
                        new ClassPathResource("application.yml")
                )
                .forEach(environment.getPropertySources()::addLast);

        StrategyAverageTrueRangeProperties properties = Binder.get(environment)
                .bind(
                        "strategy.average-true-range",
                        Bindable.of(StrategyAverageTrueRangeProperties.class)
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Strategy average true range periods must be "
                                + "configured."
                ));

        assertThat(properties.periods()).singleElement()
                .satisfies(configured -> {
                    assertThat(configured.strategyId())
                            .isEqualTo("SWING_V1");
                    assertThat(configured.strategyVersion()).isEqualTo(1);
                    assertThat(configured.horizon())
                            .isEqualTo(InvestmentHorizon.SWING);
                    assertThat(configured.period()).isEqualTo(14);
                });
    }
}
