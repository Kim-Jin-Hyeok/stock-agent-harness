package com.stock.harness;

public record HarnessRunLimits(
        int maxSteps,
        int maxToolCalls,
        int maxToolRetries,
        int maxProviderCalls
) {
    public HarnessRunLimits(int maxSteps, int maxToolCalls) {
        this(maxSteps, maxToolCalls, 0, maxToolCalls);
    }

    public HarnessRunLimits(int maxSteps, int maxToolCalls, int maxToolRetries) {
        this(maxSteps, maxToolCalls, maxToolRetries, maxToolCalls);
    }
}
