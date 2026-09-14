package com.stock.harness;

public record HarnessRunLimits(
        int maxSteps,
        int maxToolCalls,
        int maxToolRetries
) {
    public HarnessRunLimits(int maxSteps, int maxToolCalls) {
        this(maxSteps, maxToolCalls, 0);
    }
}
