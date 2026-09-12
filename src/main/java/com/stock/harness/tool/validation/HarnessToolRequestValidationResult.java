package com.stock.harness.tool.validation;

import com.stock.harness.tool.HarnessToolType;

public record HarnessToolRequestValidationResult(
        HarnessToolRequestValidationStatus status,
        HarnessToolType type,
        HarnessToolRequestValidationReasonCode reasonCode,
        String reason
) {
    public static HarnessToolRequestValidationResult valid(HarnessToolType type) {
        return new HarnessToolRequestValidationResult(
                HarnessToolRequestValidationStatus.VALID,
                type,
                HarnessToolRequestValidationReasonCode.TOOL_REQUEST_VALID,
                "Harness tool request is valid."
        );
    }

    public static HarnessToolRequestValidationResult invalid(
            HarnessToolType type,
            HarnessToolRequestValidationReasonCode reasonCode,
            String reason
    ) {
        return new HarnessToolRequestValidationResult(
                HarnessToolRequestValidationStatus.INVALID,
                type,
                reasonCode,
                reason
        );
    }
}
