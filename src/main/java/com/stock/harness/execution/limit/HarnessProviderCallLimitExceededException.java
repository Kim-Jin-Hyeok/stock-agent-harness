package com.stock.harness.execution.limit;

public class HarnessProviderCallLimitExceededException extends RuntimeException {
    public HarnessProviderCallLimitExceededException(int usedCalls, int maxCalls) {
        super("Provider call limit exceeded. used=" + usedCalls + ", max=" + maxCalls);
    }
}
