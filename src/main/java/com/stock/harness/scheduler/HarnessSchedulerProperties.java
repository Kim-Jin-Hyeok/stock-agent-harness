package com.stock.harness.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "harness.scheduler")
public record HarnessSchedulerProperties(
        boolean enabled
) {
}
