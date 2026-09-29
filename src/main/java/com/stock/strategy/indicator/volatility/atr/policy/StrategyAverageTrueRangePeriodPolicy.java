package com.stock.strategy.indicator.volatility.atr.policy;

import com.stock.strategy.data.history.StrategyDailyPriceHistoryPolicy;
import com.stock.strategy.indicator.volatility.atr.config.ConfiguredStrategyAverageTrueRangePeriod;
import com.stock.strategy.indicator.volatility.atr.config.StrategyAverageTrueRangeProperties;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class StrategyAverageTrueRangePeriodPolicy {
    private final Map<InvestmentStrategyIdentity, Integer> periodByStrategy;

    public StrategyAverageTrueRangePeriodPolicy(
            StrategyAverageTrueRangeProperties properties,
            StrategyDailyPriceHistoryPolicy dailyPriceHistoryPolicy
    ) {
        Objects.requireNonNull(properties, "properties must not be null.");
        Objects.requireNonNull(
                dailyPriceHistoryPolicy,
                "dailyPriceHistoryPolicy must not be null."
        );

        properties.periods().forEach(configured -> validateHistoryLimit(
                configured,
                dailyPriceHistoryPolicy
        ));
        periodByStrategy = properties.periods().stream()
                .collect(Collectors.toUnmodifiableMap(
                        ConfiguredStrategyAverageTrueRangePeriod::strategyIdentity,
                        ConfiguredStrategyAverageTrueRangePeriod::period
                ));
    }

    public int getPeriod(
            InvestmentStrategyIdentity strategyIdentity
    ) {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        Integer period = periodByStrategy.get(strategyIdentity);
        if (period == null) {
            throw new IllegalArgumentException(
                    "Strategy average true range period not found: "
                            + strategyIdentity
            );
        }
        return period;
    }

    private void validateHistoryLimit(
            ConfiguredStrategyAverageTrueRangePeriod configured,
            StrategyDailyPriceHistoryPolicy dailyPriceHistoryPolicy
    ) {
        InvestmentStrategyIdentity identity = configured.strategyIdentity();
        int latestBarCount = dailyPriceHistoryPolicy.getLatestBarCount(identity);
        long requiredBarCount = (long) configured.period() + 1;
        if (requiredBarCount > latestBarCount) {
            throw new IllegalArgumentException(
                    "Average true range analysis requires more daily price "
                            + "bars than configured. strategyIdentity="
                            + identity
                            + ", period="
                            + configured.period()
                            + ", requiredBarCount="
                            + requiredBarCount
                            + ", latestBarCount="
                            + latestBarCount
            );
        }
    }
}
