package com.stock.strategy.indicator.volatility.atr.config;

import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@ConfigurationProperties(prefix = "strategy.average-true-range")
public record StrategyAverageTrueRangeProperties(
        List<ConfiguredStrategyAverageTrueRangePeriod> periods
) {
    public StrategyAverageTrueRangeProperties {
        periods = List.copyOf(Objects.requireNonNull(
                periods,
                "Strategy average true range periods must not be null."
        ));
        if (periods.isEmpty()) {
            throw new IllegalArgumentException(
                    "Strategy average true range periods must not be empty."
            );
        }

        Set<InvestmentStrategyIdentity> identities = new HashSet<>();
        for (ConfiguredStrategyAverageTrueRangePeriod configured : periods) {
            Objects.requireNonNull(
                    configured,
                    "Configured strategy average true range period "
                            + "must not be null."
            );
            InvestmentStrategyIdentity identity =
                    configured.strategyIdentity();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException(
                        "Duplicate strategy average true range period: "
                                + identity
                );
            }
        }
    }
}
