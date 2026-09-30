package com.stock.market.index.history.collection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
        prefix = "market.index.history.collection.bootstrap"
)
public record MarketIndexDailyHistoryBootstrapProperties(
        boolean enabled
) {
}
