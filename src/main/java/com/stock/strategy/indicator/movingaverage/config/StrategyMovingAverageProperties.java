package com.stock.strategy.indicator.movingaverage.config;

import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@ConfigurationProperties(prefix = "strategy.moving-average")
public record StrategyMovingAverageProperties(
        List<ConfiguredStrategyMovingAveragePeriods> periods
) {
    public StrategyMovingAverageProperties {
        periods = List.copyOf(Objects.requireNonNull(
                periods,
                "Strategy moving average periods must not be null."
        ));
        if (periods.isEmpty()) {
            throw new IllegalArgumentException(
                    "Strategy moving average periods must not be empty."
            );
        }

        Set<InvestmentStrategyIdentity> identities = new HashSet<>();
        for (ConfiguredStrategyMovingAveragePeriods configured : periods) {
            Objects.requireNonNull(
                    configured,
                    "Configured strategy moving average periods "
                            + "must not be null."
            );
            InvestmentStrategyIdentity identity =
                    configured.strategyIdentity();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException(
                        "Duplicate strategy moving average periods: "
                                + identity
                );
            }
        }
    }
}
