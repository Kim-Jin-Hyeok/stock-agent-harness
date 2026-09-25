package com.stock.strategy.data.history;

import com.stock.strategy.data.history.config.ConfiguredStrategyDailyPriceHistoryLimit;
import com.stock.strategy.data.history.config.StrategyDailyPriceHistoryProperties;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class StrategyDailyPriceHistoryPolicy {
    private final Map<InvestmentStrategyIdentity, Integer>
            latestBarCountByStrategy;

    public StrategyDailyPriceHistoryPolicy(
            StrategyDailyPriceHistoryProperties properties
    ) {
        Objects.requireNonNull(properties, "properties must not be null.");
        latestBarCountByStrategy = properties.limits().stream()
                .collect(Collectors.toUnmodifiableMap(
                        ConfiguredStrategyDailyPriceHistoryLimit::strategyIdentity,
                        ConfiguredStrategyDailyPriceHistoryLimit::latestBarCount
                ));
    }

    public int getLatestBarCount(
            InvestmentStrategyIdentity strategyIdentity
    ) {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        Integer latestBarCount = latestBarCountByStrategy.get(
                strategyIdentity
        );
        if (latestBarCount == null) {
            throw new IllegalArgumentException(
                    "Strategy daily price history limit not found: "
                    + strategyIdentity
            );
        }
        return latestBarCount;
    }
}
