package com.stock.harness.scheduler;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "harness.scheduler")
public record HarnessSchedulerProperties(
        boolean enabled,
        String strategyId,
        int strategyVersion,
        InvestmentHorizon horizon
) {
    public InvestmentStrategyIdentity strategyIdentity() {
        return new InvestmentStrategyIdentity(strategyId, strategyVersion, horizon);
    }
}
