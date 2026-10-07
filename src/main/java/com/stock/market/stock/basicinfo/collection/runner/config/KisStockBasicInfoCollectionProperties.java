package com.stock.market.stock.basicinfo.collection.runner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market.stock.basic-info.collection.manual")
public record KisStockBasicInfoCollectionProperties(boolean enabled, String symbol) {
    public KisStockBasicInfoCollectionProperties {
        if (enabled && (symbol == null || !symbol.matches("[0-9A-Z]{6}"))) {
            throw new IllegalArgumentException("symbol must be exactly 6 uppercase alphanumeric characters.");
        }
    }
}
