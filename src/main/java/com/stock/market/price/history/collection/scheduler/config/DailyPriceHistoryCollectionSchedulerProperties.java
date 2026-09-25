package com.stock.market.price.history.collection.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;

@ConfigurationProperties(
        prefix = "market.price.history.collection.scheduler"
)
public record DailyPriceHistoryCollectionSchedulerProperties(
        boolean enabled,
        String cron,
        String zoneId
) {
    public DailyPriceHistoryCollectionSchedulerProperties {
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException(
                    "Daily price history collection scheduler cron "
                            + "must not be blank."
            );
        }
        CronExpression.parse(cron);
        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalArgumentException(
                    "Daily price history collection scheduler zoneId "
                            + "must not be blank."
            );
        }
        ZoneId.of(zoneId);
    }

    public ZoneId schedulerZoneId() {
        return ZoneId.of(zoneId);
    }
}
