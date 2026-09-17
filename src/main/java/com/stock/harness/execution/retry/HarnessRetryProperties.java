package com.stock.harness.execution.retry;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "harness.retry")
public record HarnessRetryProperties(
        Duration delay
) {
    public HarnessRetryProperties {
        if (delay == null || delay.isNegative()) {
            throw new IllegalArgumentException("Tool retry delay must not be null or negative.");
        }
    }
}
