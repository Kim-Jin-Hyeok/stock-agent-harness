package com.stock.harness.tool;

public class HarnessToolAuthorizer {

    public HarnessToolAuthorizationResult authorize(
            HarnessAllowedTools allowedTools,
            HarnessToolRequest request
    ) {
        if (allowedTools.allows(request.type())) {
            return HarnessToolAuthorizationResult.allowed(request.type());
        }

        return HarnessToolAuthorizationResult.denied(
                request.type(),
                HarnessToolAuthorizationReasonCode.TOOL_NOT_ALLOWED,
                "Harness tool is not allowed."
        );
    }
}
