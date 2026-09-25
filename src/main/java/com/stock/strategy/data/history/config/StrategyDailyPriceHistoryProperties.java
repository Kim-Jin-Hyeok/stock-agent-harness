package com.stock.strategy.data.history.config;

import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@ConfigurationProperties(prefix = "strategy.daily-price-history")
public record StrategyDailyPriceHistoryProperties(
        List<ConfiguredStrategyDailyPriceHistoryLimit> limits
) {
    public StrategyDailyPriceHistoryProperties {
        limits = List.copyOf(Objects.requireNonNull(
                limits,
                "Strategy daily price history limits must not be null."
        ));
        if (limits.isEmpty()) {
            throw new IllegalArgumentException(
                    "Strategy daily price history limits must not be empty."
            );
        }

        Set<InvestmentStrategyIdentity> identities = new HashSet<>();
        for (ConfiguredStrategyDailyPriceHistoryLimit limit : limits) {
            Objects.requireNonNull(
                    limit,
                    "Configured strategy daily price history limit "
                    + "must not be null."
            );
            InvestmentStrategyIdentity identity = limit.strategyIdentity();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException(
                        "Duplicate strategy daily price history limit: "
                        + identity
                );
            }
        }
    }
}
