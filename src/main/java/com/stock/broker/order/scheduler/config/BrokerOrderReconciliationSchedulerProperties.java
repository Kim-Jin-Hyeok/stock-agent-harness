package com.stock.broker.order.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "broker.order.reconciliation.scheduler")
public record BrokerOrderReconciliationSchedulerProperties(
        boolean enabled,
        long fixedDelayMs
) {
    public BrokerOrderReconciliationSchedulerProperties {
        if (fixedDelayMs <= 0) {
            throw new IllegalArgumentException(
                    "Broker order reconciliation scheduler fixedDelayMs "
                            + "must be positive."
            );
        }
    }
}
