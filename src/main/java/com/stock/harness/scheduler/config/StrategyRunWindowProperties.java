package com.stock.harness.scheduler.config;

import com.stock.harness.scheduler.window.StrategyRunWindow;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

public record StrategyRunWindowProperties(
        Set<DayOfWeek> allowedDays,
        LocalTime startsAt,
        LocalTime endsAt
) {
    public StrategyRunWindowProperties {
        StrategyRunWindow runWindow = new StrategyRunWindow(allowedDays, startsAt, endsAt);
        allowedDays = runWindow.allowedDays();
    }

    public StrategyRunWindow toRunWindow() {
        return new StrategyRunWindow(allowedDays, startsAt, endsAt);
    }
}
