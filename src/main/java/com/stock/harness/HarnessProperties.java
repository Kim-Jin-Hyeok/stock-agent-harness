package com.stock.harness;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "harness")
public record HarnessProperties(
        int maxSteps,
        int maxToolCalls,
        int maxToolRetries
) {
    public HarnessProperties(int maxSteps, int maxToolCalls) {
        this(maxSteps, maxToolCalls, 0);
    }
}
