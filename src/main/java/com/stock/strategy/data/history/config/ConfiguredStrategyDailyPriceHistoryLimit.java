package com.stock.strategy.data.history.config;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

public record ConfiguredStrategyDailyPriceHistoryLimit(
        String strategyId,
        int strategyVersion,
        InvestmentHorizon horizon,
        int latestBarCount
) {
    public ConfiguredStrategyDailyPriceHistoryLimit {
        new InvestmentStrategyIdentity(
                strategyId,
                strategyVersion,
                horizon
        );
        if (latestBarCount < 1) {
            throw new IllegalArgumentException(
                    "latestBarCount must be at least 1."
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
