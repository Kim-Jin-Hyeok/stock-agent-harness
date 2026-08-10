package com.stock.harness;

public record HarnessStepResult(
        HarnessStepType type,
        HarnessStepStatus status,
        String message
) {
}
