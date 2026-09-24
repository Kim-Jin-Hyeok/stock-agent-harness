package com.stock.broker.order.cancellation.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "broker.order.cancellation.scheduler")
public record BrokerOrderExpirationCancellationSchedulerProperties(
        boolean enabled,
        long fixedDelayMs
) {
    public BrokerOrderExpirationCancellationSchedulerProperties {
        if (fixedDelayMs <= 0) {
            throw new IllegalArgumentException(
                    "Broker order expiration cancellation scheduler "
                            + "fixedDelayMs must be positive."
            );
        }
    }
}
