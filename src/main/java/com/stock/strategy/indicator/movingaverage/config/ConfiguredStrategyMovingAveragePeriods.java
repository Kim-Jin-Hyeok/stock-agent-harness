package com.stock.strategy.indicator.movingaverage.config;

import com.stock.strategy.indicator.movingaverage.MovingAveragePeriods;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

public record ConfiguredStrategyMovingAveragePeriods(
        String strategyId,
        int strategyVersion,
        InvestmentHorizon horizon,
        int shortPeriod,
        int longPeriod
) {
    public ConfiguredStrategyMovingAveragePeriods {
        new InvestmentStrategyIdentity(
                strategyId,
                strategyVersion,
                horizon
        );
        new MovingAveragePeriods(shortPeriod, longPeriod);
    }

    public InvestmentStrategyIdentity strategyIdentity() {
        return new InvestmentStrategyIdentity(
                strategyId,
                strategyVersion,
                horizon
        );
    }

    public MovingAveragePeriods periods() {
        return new MovingAveragePeriods(shortPeriod, longPeriod);
    }
}
