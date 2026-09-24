package com.stock.strategy.universe;

import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.universe.config.ConfiguredStrategyStockUniverse;
import com.stock.strategy.universe.config.StrategyStockUniverseProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class StrategyStockUniverseRegistry {
    private final Map<InvestmentStrategyIdentity, List<String>> symbolsByStrategy;

    public StrategyStockUniverseRegistry(
            StrategyStockUniverseProperties properties
    ) {
        Objects.requireNonNull(properties, "properties must not be null.");
        symbolsByStrategy = properties.universes().stream()
                .collect(Collectors.toUnmodifiableMap(
                        ConfiguredStrategyStockUniverse::strategyIdentity,
                        universe -> List.copyOf(universe.symbols())
                ));
    }

    public List<String> getCandidateSymbols(
            InvestmentStrategyIdentity strategyIdentity
    ) {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        List<String> symbols = symbolsByStrategy.get(strategyIdentity);
        if (symbols == null) {
            throw new IllegalArgumentException(
                    "Strategy stock universe not found: " + strategyIdentity
            );
        }
        return symbols;
    }
}
