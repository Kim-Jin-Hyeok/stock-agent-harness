package com.stock.strategy.universe.config;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ConfiguredStrategyStockUniverse(
        String strategyId,
        int strategyVersion,
        InvestmentHorizon horizon,
        List<String> symbols
) {
    public ConfiguredStrategyStockUniverse {
        new InvestmentStrategyIdentity(
                strategyId,
                strategyVersion,
                horizon
        );
        symbols = List.copyOf(Objects.requireNonNull(
                symbols,
                "Strategy stock universe symbols must not be null."
        ));
        if (symbols.isEmpty()) {
            throw new IllegalArgumentException(
                    "Strategy stock universe symbols must not be empty."
            );
        }

        Set<String> uniqueSymbols = new HashSet<>();
        for (String symbol : symbols) {
            if (symbol == null || symbol.isBlank()) {
                throw new IllegalArgumentException(
                        "Strategy stock universe symbol must not be blank."
                );
            }
            if (!uniqueSymbols.add(symbol)) {
                throw new IllegalArgumentException(
                        "Duplicate strategy stock universe symbol: " + symbol
                );
            }
        }
    }

    public InvestmentStrategyIdentity strategyIdentity() {
        return new InvestmentStrategyIdentity(
                strategyId,
                strategyVersion,
                horizon
        );
    }
}
