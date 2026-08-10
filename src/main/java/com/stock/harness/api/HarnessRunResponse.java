package com.stock.harness.api;

import com.stock.agent.InvestmentDecision;
import com.stock.harness.HarnessRunResult;
import com.stock.harness.HarnessRunStatus;
import com.stock.harness.HarnessStepResult;
import com.stock.risk.RiskCheckResult;
import com.stock.trade.TradeResult;

import java.time.LocalDateTime;
import java.util.List;

public record HarnessRunResponse(
        String runId,
        HarnessRunStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<HarnessStepResult> steps,
        InvestmentDecision decision,
        RiskCheckResult riskCheckResult,
        TradeResult tradeResult
) {
    public static HarnessRunResponse from(HarnessRunResult result) {
        return new HarnessRunResponse(
                result.runId(),
                result.status(),
                result.startedAt(),
                result.finishedAt(),
                result.steps(),
                result.decision(),
                result.riskCheckResult(),
                result.tradeResult()
        );
    }
}