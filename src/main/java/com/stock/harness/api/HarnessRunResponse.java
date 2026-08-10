package com.stock.harness.api;

import com.stock.harness.HarnessRunResult;
import com.stock.harness.HarnessRunStatus;

import java.time.LocalDateTime;
import java.util.List;

public record HarnessRunResponse(
        String runId,
        HarnessRunStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<String> steps
) {
    public static HarnessRunResponse from(HarnessRunResult result) {
        return new HarnessRunResponse(
                result.runId(),
                result.status(),
                result.startedAt(),
                result.finishedAt(),
                result.steps()
        );
    }
}