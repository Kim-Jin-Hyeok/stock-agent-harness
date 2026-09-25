package com.stock.market.price.history.collection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
        prefix = "market.price.history.collection.bootstrap"
)
public record DailyPriceHistoryBootstrapProperties(
        boolean enabled
) {
}
