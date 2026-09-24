package com.stock.harness;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "harness")
public record HarnessProperties(
        int maxSteps,
        int maxToolCalls,
        int maxToolRetries,
        int maxProviderCalls
) {
    @ConstructorBinding
    public HarnessProperties {
    }

    public HarnessProperties(int maxSteps, int maxToolCalls) {
        this(maxSteps, maxToolCalls, 0, maxToolCalls);
    }

    public HarnessProperties(int maxSteps, int maxToolCalls, int maxToolRetries) {
        this(maxSteps, maxToolCalls, maxToolRetries, maxToolCalls);
    }
}
