package com.stock.harness.api;

import com.stock.agent.InvestmentDecision;
import com.stock.harness.HarnessRunResult;
import com.stock.harness.HarnessRunStatus;

import java.time.LocalDateTime;
import java.util.List;

public record HarnessRunResponse(
        String runId,
        HarnessRunStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<String> steps,
        InvestmentDecision decision
) {
    public static HarnessRunResponse from(HarnessRunResult result) {
        return new HarnessRunResponse(
                result.runId(),
                result.status(),
                result.startedAt(),
                result.finishedAt(),
                result.steps(),
                result.decision()
        );
    }
}