package com.stock.harness.tool;

import org.springframework.stereotype.Component;

@Component
public class HarnessToolExecutor {

    public HarnessToolExecutionResult execute(HarnessToolRequest request) {
        return HarnessToolExecutionResult.notSupported(request.type());
    }
}
