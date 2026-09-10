package com.stock.harness.execution.limit;

public class HarnessAgentStepBudget {
    private final int maxSteps;
    private int usedSteps;

    public HarnessAgentStepBudget(int maxSteps) {
        if (maxSteps < 1) {
            throw new IllegalArgumentException("maxSteps must be at least 1.");
        }

        this.maxSteps = maxSteps;
    }

    public boolean tryConsume() {
        if (usedSteps >= maxSteps) {
            return false;
        }

        usedSteps++;
        return true;
    }

    public int usedSteps() {
        return usedSteps;
    }

    public int maxSteps() {
        return maxSteps;
    }
}
