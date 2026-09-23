package com.stock.portfolio.initialization.allocation.config;

import com.stock.strategy.profile.InvestmentHorizon;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class StrategyPortfolioAllocationPropertiesYamlBindingTest {

    @Test
    void bindsStrategyAllocationsFromApplicationYaml() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        StrategyPortfolioAllocationProperties properties = Binder.get(environment)
                .bind(
                        "portfolio.initialization",
                        Bindable.of(StrategyPortfolioAllocationProperties.class)
                )
                .orElseThrow(() -> new IllegalStateException(
                        "portfolio.initialization must be configured."
                ));

        assertThat(properties.allocations())
                .extracting(
                        StrategyAllocationProperties::strategyId,
                        StrategyAllocationProperties::strategyVersion,
                        StrategyAllocationProperties::horizon,
                        StrategyAllocationProperties::weight
                )
                .containsExactly(
                        tuple("DAY_TRADING_V1", 1, InvestmentHorizon.DAY_TRADING, 1L),
                        tuple("SWING_V1", 1, InvestmentHorizon.SWING, 1L),
                        tuple("LONG_TERM_V1", 1, InvestmentHorizon.LONG_TERM, 1L)
                );
    }
}
