package com.stock.portfolio.initialization.allocation.config;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

public record StrategyAllocationProperties(
        String strategyId,
        int strategyVersion,
        InvestmentHorizon horizon,
        long weight
) {
    public StrategyAllocationProperties {
        new InvestmentStrategyIdentity(strategyId, strategyVersion, horizon);
        if (weight <= 0) {
            throw new IllegalArgumentException(
                    "Strategy allocation weight must be positive."
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
