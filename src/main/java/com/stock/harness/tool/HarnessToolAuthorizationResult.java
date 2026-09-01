package com.stock.harness.tool;

public record HarnessToolAuthorizationResult(
        HarnessToolAuthorizationStatus status,
        HarnessToolType type,
        HarnessToolAuthorizationReasonCode reasonCode,
        String reason
) {
    public static HarnessToolAuthorizationResult allowed(HarnessToolType type) {
        return new HarnessToolAuthorizationResult(
                HarnessToolAuthorizationStatus.ALLOWED,
                type,
                HarnessToolAuthorizationReasonCode.TOOL_ALLOWED,
                "Harness tool authorization allowed."
        );
    }

    public static HarnessToolAuthorizationResult denied(
            HarnessToolType type,
            HarnessToolAuthorizationReasonCode reasonCode,
            String reason
    ) {
        return new HarnessToolAuthorizationResult(
                HarnessToolAuthorizationStatus.DENIED,
                type,
                reasonCode,
                reason
        );
    }
}
