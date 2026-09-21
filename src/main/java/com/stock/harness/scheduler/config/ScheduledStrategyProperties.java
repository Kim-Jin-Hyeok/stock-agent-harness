package com.stock.harness.scheduler.config;

import com.stock.harness.scheduler.window.StrategyRunWindow;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

import java.util.Objects;

public record ScheduledStrategyProperties(
        boolean enabled,
        String strategyId,
        int strategyVersion,
        InvestmentHorizon horizon,
        StrategyRunWindowProperties window
) {
    public ScheduledStrategyProperties {
        new InvestmentStrategyIdentity(strategyId, strategyVersion, horizon);
        Objects.requireNonNull(window, "window must not be null.");
    }

    public InvestmentStrategyIdentity strategyIdentity() {
        return new InvestmentStrategyIdentity(strategyId, strategyVersion, horizon);
    }

    public StrategyRunWindow runWindow() {
        return window.toRunWindow();
    }
}
