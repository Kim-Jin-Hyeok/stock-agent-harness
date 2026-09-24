package com.stock.strategy.universe.config;

import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@ConfigurationProperties(prefix = "strategy")
public record StrategyStockUniverseProperties(
        List<ConfiguredStrategyStockUniverse> universes
) {
    public StrategyStockUniverseProperties {
        universes = List.copyOf(Objects.requireNonNull(
                universes,
                "Strategy stock universes must not be null."
        ));
        if (universes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Strategy stock universes must not be empty."
            );
        }

        Set<InvestmentStrategyIdentity> identities = new HashSet<>();
        for (ConfiguredStrategyStockUniverse universe : universes) {
            Objects.requireNonNull(
                    universe,
                    "Configured strategy stock universe must not be null."
            );
            InvestmentStrategyIdentity identity = universe.strategyIdentity();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException(
                        "Duplicate strategy stock universe: " + identity
                );
            }
        }
    }
}
