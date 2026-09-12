package com.stock.harness.tool.validation;

import com.stock.harness.tool.HarnessToolType;

public record HarnessToolResultValidationResult(
        HarnessToolResultValidationStatus status,
        HarnessToolType type,
        HarnessToolResultValidationReasonCode reasonCode,
        String reason
) {
    public static HarnessToolResultValidationResult valid(HarnessToolType type) {
        return new HarnessToolResultValidationResult(
                HarnessToolResultValidationStatus.VALID,
                type,
                HarnessToolResultValidationReasonCode.TOOL_RESULT_VALID,
                "Harness tool result is valid."
        );
    }

    public static HarnessToolResultValidationResult invalid(
            HarnessToolType type,
            HarnessToolResultValidationReasonCode reasonCode,
            String reason
    ) {
        return new HarnessToolResultValidationResult(
                HarnessToolResultValidationStatus.INVALID,
                type,
                reasonCode,
                reason
        );
    }
}
