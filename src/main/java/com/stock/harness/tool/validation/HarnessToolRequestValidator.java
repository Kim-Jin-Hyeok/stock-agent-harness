package com.stock.harness.tool.validation;

import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
import org.springframework.stereotype.Component;

@Component
public class HarnessToolRequestValidator {

    public HarnessToolRequestValidationResult validate(HarnessToolRequest request) {
        if (request == null) {
            return HarnessToolRequestValidationResult.invalid(
                    null,
                    HarnessToolRequestValidationReasonCode.REQUEST_MISSING,
                    "Tool request is missing."
            );
        }

        if (request.type() == null) {
            return HarnessToolRequestValidationResult.invalid(
                    null,
                    HarnessToolRequestValidationReasonCode.TOOL_TYPE_MISSING,
                    "Tool request type is missing."
            );
        }

        if (request.type() == HarnessToolType.GET_CURRENT_PRICE
                && (request.symbol() == null || request.symbol().isBlank())) {
            return HarnessToolRequestValidationResult.invalid(
                    request.type(),
                    HarnessToolRequestValidationReasonCode.SYMBOL_MISSING,
                    "Symbol is required for current price request."
            );
        }

        return HarnessToolRequestValidationResult.valid(request.type());
    }
}
