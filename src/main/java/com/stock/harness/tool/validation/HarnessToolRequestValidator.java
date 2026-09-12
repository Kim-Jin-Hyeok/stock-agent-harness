package com.stock.harness.tool.validation;

import com.stock.harness.tool.HarnessToolRequest;
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

        return HarnessToolRequestValidationResult.valid(request.type());
    }
}
