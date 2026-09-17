package com.stock.harness;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "harness")
public record HarnessProperties(
        int maxSteps,
        int maxToolCalls,
        int maxToolRetries,
        int maxProviderCalls
) {
    public HarnessProperties(int maxSteps, int maxToolCalls) {
        this(maxSteps, maxToolCalls, 0, maxToolCalls);
    }

    public HarnessProperties(int maxSteps, int maxToolCalls, int maxToolRetries) {
        this(maxSteps, maxToolCalls, maxToolRetries, maxToolCalls);
    }
}
