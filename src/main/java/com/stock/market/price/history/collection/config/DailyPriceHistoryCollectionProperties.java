package com.stock.market.price.history.collection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;
import java.util.Objects;

@ConfigurationProperties(prefix = "market.price.history.collection")
public record DailyPriceHistoryCollectionProperties(
        LocalTime dailyBarAvailableAt
) {
    public DailyPriceHistoryCollectionProperties {
        Objects.requireNonNull(
                dailyBarAvailableAt,
                "dailyBarAvailableAt must not be null."
        );
    }
}
