package com.stock.strategy.data.history.config;

import com.stock.strategy.profile.InvestmentHorizon;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class StrategyDailyPriceHistoryPropertiesYamlBindingTest {

    @Test
    void bindsStrategyDailyPriceHistoryLimitsFromApplicationYaml()
            throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load(
                        "application.yml",
                        new ClassPathResource("application.yml")
                )
                .forEach(environment.getPropertySources()::addLast);

        StrategyDailyPriceHistoryProperties properties =
                Binder.get(environment)
                        .bind(
                                "strategy.daily-price-history",
                                Bindable.of(
                                        StrategyDailyPriceHistoryProperties.class
                                )
                        )
                        .orElseThrow(() -> new IllegalStateException(
                                "Strategy daily price history must be configured."
                        ));

        assertThat(properties.limits()).hasSize(3);
        assertThat(properties.limits())
                .extracting(
                        ConfiguredStrategyDailyPriceHistoryLimit::horizon,
                        ConfiguredStrategyDailyPriceHistoryLimit::latestBarCount
                )
                .containsExactly(
                        tuple(
                                InvestmentHorizon.DAY_TRADING,
                                60
                        ),
                        tuple(
                                InvestmentHorizon.SWING,
                                120
                        ),
                        tuple(
                                InvestmentHorizon.LONG_TERM,
                                250
                        )
                );
    }
}
