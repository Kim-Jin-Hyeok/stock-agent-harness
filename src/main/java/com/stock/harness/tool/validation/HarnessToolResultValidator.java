package com.stock.harness.tool.validation;

import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolOutput;
import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
import org.springframework.stereotype.Component;

@Component
public class HarnessToolResultValidator {

    public HarnessToolResultValidationResult validate(
            HarnessToolRequest request,
            HarnessToolExecutionResult result
    ) {
        HarnessToolType requestedType = request.type();

        if (result.type() != requestedType) {
            return HarnessToolResultValidationResult.invalid(
                    requestedType,
                    HarnessToolResultValidationReasonCode.RESULT_TYPE_MISMATCH,
                    "Tool result type does not match request. requested="
                    + requestedType
                    + ", actual="
                    + result.type()
            );
        }

        HarnessToolOutput output = result.output();

        if (output == null) {
            return HarnessToolResultValidationResult.invalid(
                    requestedType,
                    HarnessToolResultValidationReasonCode.OUTPUT_MISSING,
                    "Tool output is missing. type=" + requestedType
            );
        }

        if (output.type() != requestedType) {
            return HarnessToolResultValidationResult.invalid(
                    requestedType,
                    HarnessToolResultValidationReasonCode.OUTPUT_TYPE_MISMATCH,
                    "Tool output type does not match request. requested="
                    + requestedType
                    + ", actual="
                    + output.type()
            );
        }

        if (isExpectedPayloadMissing(requestedType, output)) {
            return HarnessToolResultValidationResult.invalid(
                    requestedType,
                    HarnessToolResultValidationReasonCode.OUTPUT_PAYLOAD_MISSING,
                    "Expected tool output payload is missing. type=" + requestedType
            );
        }

        return HarnessToolResultValidationResult.valid(requestedType);
    }

    private boolean isExpectedPayloadMissing(
            HarnessToolType type,
            HarnessToolOutput output
    ) {
        return switch (type) {
            case GET_PORTFOLIO -> output.portfolioSnapshot() == null;
            case GET_MARKET -> output.marketSnapshot() == null;
            case GET_CURRENT_PRICE -> output.currentPriceSnapshot() == null;
        };
    }
}
