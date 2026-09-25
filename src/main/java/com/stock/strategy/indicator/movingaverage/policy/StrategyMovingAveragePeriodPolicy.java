package com.stock.strategy.indicator.movingaverage.policy;

import com.stock.strategy.data.history.StrategyDailyPriceHistoryPolicy;
import com.stock.strategy.indicator.movingaverage.MovingAveragePeriods;
import com.stock.strategy.indicator.movingaverage.config.ConfiguredStrategyMovingAveragePeriods;
import com.stock.strategy.indicator.movingaverage.config.StrategyMovingAverageProperties;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class StrategyMovingAveragePeriodPolicy {
    private final Map<InvestmentStrategyIdentity, MovingAveragePeriods>
            periodsByStrategy;

    public StrategyMovingAveragePeriodPolicy(
            StrategyMovingAverageProperties properties,
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
        periodsByStrategy = properties.periods().stream()
                .collect(Collectors.toUnmodifiableMap(
                        ConfiguredStrategyMovingAveragePeriods::strategyIdentity,
                        ConfiguredStrategyMovingAveragePeriods::periods
                ));
    }

    public MovingAveragePeriods getPeriods(
            InvestmentStrategyIdentity strategyIdentity
    ) {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        MovingAveragePeriods periods = periodsByStrategy.get(
                strategyIdentity
        );
        if (periods == null) {
            throw new IllegalArgumentException(
                    "Strategy moving average periods not found: "
                            + strategyIdentity
            );
        }
        return periods;
    }

    private void validateHistoryLimit(
            ConfiguredStrategyMovingAveragePeriods configured,
            StrategyDailyPriceHistoryPolicy dailyPriceHistoryPolicy
    ) {
        InvestmentStrategyIdentity identity = configured.strategyIdentity();
        int latestBarCount = dailyPriceHistoryPolicy.getLatestBarCount(
                identity
        );
        long requiredBarCount = (long) configured.longPeriod() + 1;
        if (requiredBarCount > latestBarCount) {
            throw new IllegalArgumentException(
                    "Moving average crossover analysis requires more daily "
                            + "price bars than configured. strategyIdentity="
                            + identity
                            + ", longPeriod="
                            + configured.longPeriod()
                            + ", requiredBarCount="
                            + requiredBarCount
                            + ", latestBarCount="
                            + latestBarCount
            );
        }
    }
}
