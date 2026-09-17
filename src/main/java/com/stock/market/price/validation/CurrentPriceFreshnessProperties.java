package com.stock.market.price.validation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "market.current-price.validation")
public record CurrentPriceFreshnessProperties(
        Duration maxAge
) {
    public CurrentPriceFreshnessProperties {
        if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalArgumentException("Current price max age must be positive.");
        }
    }
}
