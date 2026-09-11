package com.stock.harness.execution.limit;

public class HarnessToolCallBudget {
    private final int maxCalls;
    private int usedCalls;

    public HarnessToolCallBudget(int maxCalls) {
        if (maxCalls < 0) {
            throw new IllegalArgumentException("maxCalls must not be negative.");
        }

        this.maxCalls = maxCalls;
    }

    public boolean tryConsume() {
        if (usedCalls >= maxCalls) {
            return false;
        }

        usedCalls++;
        return true;
    }

    public int usedCalls() {
        return usedCalls;
    }

    public int maxCalls() {
        return maxCalls;
    }
}
