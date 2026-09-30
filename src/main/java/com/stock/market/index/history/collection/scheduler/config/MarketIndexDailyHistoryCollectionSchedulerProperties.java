package com.stock.market.index.history.collection.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;

@ConfigurationProperties(
        prefix = "market.index.history.collection.scheduler"
)
public record MarketIndexDailyHistoryCollectionSchedulerProperties(
        boolean enabled,
        String cron,
        String zoneId
) {
    public MarketIndexDailyHistoryCollectionSchedulerProperties {
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException(
                    "Market index daily history collection scheduler cron "
                            + "must not be blank."
            );
        }
        CronExpression.parse(cron);
        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalArgumentException(
                    "Market index daily history collection scheduler zoneId "
                            + "must not be blank."
            );
        }
        ZoneId.of(zoneId);
    }

    public ZoneId schedulerZoneId() {
        return ZoneId.of(zoneId);
    }
}
