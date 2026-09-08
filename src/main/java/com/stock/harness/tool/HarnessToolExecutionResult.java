package com.stock.harness.tool;

public record HarnessToolExecutionResult(
        HarnessToolExecutionStatus status,
        HarnessToolType type,
        HarnessToolExecutionReasonCode reasonCode,
        String reason
) {
    public static HarnessToolExecutionResult executed(HarnessToolType type) {
        return new HarnessToolExecutionResult(
                HarnessToolExecutionStatus.EXECUTED,
                type,
                HarnessToolExecutionReasonCode.TOOL_EXECUTED,
                "Harness tool execution completed."
        );
    }

    public static HarnessToolExecutionResult notSupported(HarnessToolType type) {
        return new HarnessToolExecutionResult(
                HarnessToolExecutionStatus.FAILED,
                type,
                HarnessToolExecutionReasonCode.TOOL_NOT_SUPPORTED,
                "Tool execution is not supported yet."
        );
    }

    public static HarnessToolExecutionResult authorizationDenied(HarnessToolType type) {
        return new HarnessToolExecutionResult(
                HarnessToolExecutionStatus.FAILED,
                type,
                HarnessToolExecutionReasonCode.TOOL_AUTHORIZATION_DENIED,
                "Tool authorization denied."
        );
    }
}
