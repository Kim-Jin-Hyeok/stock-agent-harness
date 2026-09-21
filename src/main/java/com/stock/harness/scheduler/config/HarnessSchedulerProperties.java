package com.stock.harness.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@ConfigurationProperties(prefix = "harness.scheduler")
public record HarnessSchedulerProperties(
        boolean enabled,
        String zoneId,
        List<ScheduledStrategyProperties> strategies
) {
    public HarnessSchedulerProperties {
        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalArgumentException("Scheduler zoneId must not be blank.");
        }
        ZoneId.of(zoneId);
        strategies = List.copyOf(Objects.requireNonNull(
                strategies,
                "Scheduler strategies must not be null."
        ));
    }

    public ZoneId schedulerZoneId() {
        return ZoneId.of(zoneId);
    }
}
