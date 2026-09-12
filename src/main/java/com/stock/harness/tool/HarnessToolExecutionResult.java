package com.stock.harness.tool;

public record HarnessToolExecutionResult(
        HarnessToolExecutionStatus status,
        HarnessToolType type,
        HarnessToolExecutionReasonCode reasonCode,
        String reason,
        HarnessToolOutput output
) {
    public static HarnessToolExecutionResult executed(HarnessToolOutput output) {
        return new HarnessToolExecutionResult(
                HarnessToolExecutionStatus.EXECUTED,
                output.type(),
                HarnessToolExecutionReasonCode.TOOL_EXECUTED,
                "Harness tool execution completed.",
                output
        );
    }

    public static HarnessToolExecutionResult executionFailed(
            HarnessToolType type,
            String cause
    ) {
        return new HarnessToolExecutionResult(
                HarnessToolExecutionStatus.FAILED,
                type,
                HarnessToolExecutionReasonCode.TOOL_EXECUTION_FAILED,
                "Tool execution failed. cause=" + cause,
                null
        );
    }

    public static HarnessToolExecutionResult notSupported(HarnessToolType type) {
        return new HarnessToolExecutionResult(
                HarnessToolExecutionStatus.FAILED,
                type,
                HarnessToolExecutionReasonCode.TOOL_NOT_SUPPORTED,
                "Tool execution is not supported yet.",
                null
        );
    }

    public static HarnessToolExecutionResult authorizationDenied(HarnessToolType type) {
        return new HarnessToolExecutionResult(
                HarnessToolExecutionStatus.FAILED,
                type,
                HarnessToolExecutionReasonCode.TOOL_AUTHORIZATION_DENIED,
                "Tool authorization denied.",
                null
        );
    }

    public static HarnessToolExecutionResult duplicateRequest(HarnessToolType type) {
        return new HarnessToolExecutionResult(
                HarnessToolExecutionStatus.SKIPPED,
                type,
                HarnessToolExecutionReasonCode.DUPLICATE_TOOL_REQUEST,
                "Duplicate tool request was skipped.",
                null
        );
    }
}
