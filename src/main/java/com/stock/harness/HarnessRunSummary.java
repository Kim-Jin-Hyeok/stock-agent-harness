package com.stock.harness;

import java.time.LocalDateTime;

public record HarnessRunSummary(
        String runId,
        HarnessRunStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
