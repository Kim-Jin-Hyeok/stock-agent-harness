package com.stock.harness;

import java.time.LocalDateTime;

public record HarnessStepResult(
        HarnessStepType type,
        HarnessStepStatus status,
        String message,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
