package com.stock.harness.scheduler.config;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

public record ScheduledStrategyProperties(
        boolean enabled,
        String strategyId,
        int strategyVersion,
        InvestmentHorizon horizon
) {
    public ScheduledStrategyProperties {
        new InvestmentStrategyIdentity(strategyId, strategyVersion, horizon);
    }

    public InvestmentStrategyIdentity strategyIdentity() {
        return new InvestmentStrategyIdentity(strategyId, strategyVersion, horizon);
    }
}
