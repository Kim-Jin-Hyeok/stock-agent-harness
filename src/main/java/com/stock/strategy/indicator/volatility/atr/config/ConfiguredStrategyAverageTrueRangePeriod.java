package com.stock.strategy.indicator.volatility.atr.config;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

public record ConfiguredStrategyAverageTrueRangePeriod(
        String strategyId,
        int strategyVersion,
        InvestmentHorizon horizon,
        int period
) {
    public ConfiguredStrategyAverageTrueRangePeriod {
        new InvestmentStrategyIdentity(
                strategyId,
                strategyVersion,
                horizon
        );
        if (period < 1) {
            throw new IllegalArgumentException(
                    "period must be at least 1."
            );
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
