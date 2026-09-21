package com.stock.harness.scheduler.policy;

import com.stock.harness.scheduler.window.StrategyRunWindow;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

@Component
public class StrategyRunWindowPolicy {

    public boolean isWithinWindow(
            StrategyRunWindow window,
            LocalDateTime evaluatedAt
    ) {
        Objects.requireNonNull(window, "window must not be null.");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null.");

        if (!window.allowedDays().contains(evaluatedAt.getDayOfWeek())) {
            return false;
        }

        LocalTime evaluatedTime = evaluatedAt.toLocalTime();
        return !evaluatedTime.isBefore(window.startsAt())
                && !evaluatedTime.isAfter(window.endsAt());
    }
}
