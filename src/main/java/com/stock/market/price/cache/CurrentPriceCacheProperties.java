package com.stock.market.price.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "market.current-price.cache")
public record CurrentPriceCacheProperties(
        Duration ttl
) {
    public CurrentPriceCacheProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Current price cache TTL must be positive.");
        }
    }
}
