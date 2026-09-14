package com.stock.harness.execution.retry;

import com.stock.harness.tool.HarnessToolExecutionReasonCode;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolExecutionStatus;

public record HarnessToolRetryPolicy(
        int maxRetries
) {
    public HarnessToolRetryPolicy {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative.");
        }
    }

    public boolean shouldRetry(
            HarnessToolExecutionResult result,
            int usedRetries
    ) {
        return result.status() == HarnessToolExecutionStatus.FAILED
                && result.reasonCode() == HarnessToolExecutionReasonCode.TOOL_EXECUTION_FAILED
                && usedRetries < maxRetries;
    }
}
