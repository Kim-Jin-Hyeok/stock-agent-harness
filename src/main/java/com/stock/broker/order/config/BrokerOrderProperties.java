package com.stock.broker.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "broker.order")
public record BrokerOrderProperties(
        Duration validity
) {
    public BrokerOrderProperties {
        if (validity == null || validity.isZero() || validity.isNegative()) {
            throw new IllegalArgumentException(
                    "Broker order validity must be positive."
            );
        }
    }
}
