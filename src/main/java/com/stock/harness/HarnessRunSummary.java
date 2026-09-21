package com.stock.harness;

import com.stock.strategy.profile.InvestmentStrategyIdentity;

import java.time.LocalDateTime;

public record HarnessRunSummary(
        String runId,
        InvestmentStrategyIdentity strategyIdentity,
        HarnessRunStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
