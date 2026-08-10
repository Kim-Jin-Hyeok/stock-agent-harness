package com.stock.harness;

import com.stock.agent.InvestmentDecision;

import java.time.LocalDateTime;
import java.util.List;

public record HarnessRunResult(
        String runId,
        HarnessRunStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<String> steps,
        InvestmentDecision decision
) {
}