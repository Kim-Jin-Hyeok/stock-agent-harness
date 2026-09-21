package com.stock.harness.scheduler.window;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record StrategyRunWindow(
        Set<DayOfWeek> allowedDays,
        LocalTime startsAt,
        LocalTime endsAt
) {
    public StrategyRunWindow {
        Objects.requireNonNull(allowedDays, "allowedDays must not be null.");
        Objects.requireNonNull(startsAt, "startsAt must not be null.");
        Objects.requireNonNull(endsAt, "endsAt must not be null.");

        if (allowedDays.isEmpty()) {
            throw new IllegalArgumentException("allowedDays must not be empty.");
        }
        if (startsAt.isAfter(endsAt)) {
            throw new IllegalArgumentException("startsAt must not be after endsAt.");
        }

        allowedDays = Collections.unmodifiableSet(EnumSet.copyOf(allowedDays));
    }
}
