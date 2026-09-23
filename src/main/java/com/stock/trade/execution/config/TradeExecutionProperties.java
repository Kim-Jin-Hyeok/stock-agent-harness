package com.stock.trade.execution.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

@ConfigurationProperties(prefix = "trade.execution")
public record TradeExecutionProperties(
        TradeExecutionMode mode
) {
    public TradeExecutionProperties {
        Objects.requireNonNull(mode, "Trade execution mode must not be null.");
    }
}
