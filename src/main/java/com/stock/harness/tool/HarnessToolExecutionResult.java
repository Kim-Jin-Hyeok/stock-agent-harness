package com.stock.harness.tool;

public record HarnessToolExecutionResult(
        HarnessToolExecutionStatus status,
        HarnessToolType type,
        HarnessToolRequest request,
        HarnessToolExecutionReasonCode reasonCode,
        String reason,
        HarnessToolOutput output
) {
    public HarnessToolExecutionResult(
            HarnessToolExecutionStatus status,
            HarnessToolType type,
            HarnessToolExecutionReasonCode reasonCode,
            String reason,
            HarnessToolOutput output
    ) {
        this(status, type, new HarnessToolRequest(type), reasonCode, reason, output);
    }

    public static HarnessToolExecutionResult executed(HarnessToolOutput output) {
        return executed(new HarnessToolRequest(output.type()), output);
    }

    public static HarnessToolExecutionResult executed(
            HarnessToolRequest request,
            HarnessToolOutput output
    ) {
        return new HarnessToolExecutionResult(
                HarnessToolExecutionStatus.EXECUTED,
                request.type(),
                request,
                HarnessToolExecutionReasonCode.TOOL_EXECUTED,
                "Harness tool execution completed.",
                output
        );
    }

    public static HarnessToolExecutionResult executionFailed(
            HarnessToolType type,
            String cause
    ) {
        return executionFailed(new HarnessToolRequest(type), cause);
    }

    public static HarnessToolExecutionResult executionFailed(
            HarnessToolRequest request,
            String cause
    ) {
        return new HarnessToolExecutionResult(
                HarnessToolExecutionStatus.FAILED,
                request.type(),
                request,
                HarnessToolExecutionReasonCode.TOOL_EXECUTION_FAILED,
                "Tool execution failed. cause=" + cause,
                null
        );
    }

    public static HarnessToolExecutionResult notSupported(HarnessToolType type) {
        return notSupported(new HarnessToolRequest(type));
    }

    public static HarnessToolExecutionResult notSupported(HarnessToolRequest request) {
        return new HarnessToolExecutionResult(
                HarnessToolExecutionStatus.FAILED,
                request.type(),
                request,
                HarnessToolExecutionReasonCode.TOOL_NOT_SUPPORTED,
                "Tool execution is not supported yet.",
                null
        );
    }

    public static HarnessToolExecutionResult authorizationDenied(HarnessToolType type) {
        return authorizationDenied(new HarnessToolRequest(type));
    }

    public static HarnessToolExecutionResult authorizationDenied(HarnessToolRequest request) {
        return new HarnessToolExecutionResult(
                HarnessToolExecutionStatus.FAILED,
                request.type(),
                request,
                HarnessToolExecutionReasonCode.TOOL_AUTHORIZATION_DENIED,
                "Tool authorization denied.",
                null
        );
    }

    public static HarnessToolExecutionResult duplicateRequest(HarnessToolType type) {
        return duplicateRequest(new HarnessToolRequest(type));
    }

    public static HarnessToolExecutionResult duplicateRequest(HarnessToolRequest request) {
        return new HarnessToolExecutionResult(
                HarnessToolExecutionStatus.SKIPPED,
                request.type(),
                request,
                HarnessToolExecutionReasonCode.DUPLICATE_TOOL_REQUEST,
                "Duplicate tool request was skipped.",
                null
        );
    }

    public static HarnessToolExecutionResult providerCallLimitExceeded(
            HarnessToolRequest request,
            String reason
    ) {
        return new HarnessToolExecutionResult(
                HarnessToolExecutionStatus.FAILED,
                request.type(),
                request,
                HarnessToolExecutionReasonCode.PROVIDER_CALL_LIMIT_EXCEEDED,
                reason,
                null
        );
    }
}
