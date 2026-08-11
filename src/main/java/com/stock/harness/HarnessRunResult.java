package com.stock.harness;

import com.stock.agent.InvestmentDecision;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.risk.RiskCheckResult;
import com.stock.trade.TradeResult;

import java.time.LocalDateTime;
import java.util.List;

public record HarnessRunResult(
        String runId,
        HarnessRunStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<HarnessStepResult> steps,
        InvestmentDecision decision,
        RiskCheckResult riskCheckResult,
        TradeResult tradeResult,
        PortfolioSnapshot portfolioSnapshot,
        MarketSnapshot marketSnapshot
) {
    public static HarnessRunResult of(
            String runId,
            HarnessRunStatus status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            List<HarnessStepResult> steps,
            InvestmentDecision decision,
            RiskCheckResult riskCheckResult,
            TradeResult tradeResult,
            PortfolioSnapshot portfolioSnapshot,
            MarketSnapshot marketSnapshot
    ) {
        return new HarnessRunResult(
                runId,
                status,
                startedAt,
                finishedAt,
                steps,
                decision,
                riskCheckResult,
                tradeResult,
                portfolioSnapshot,
                marketSnapshot
        );
    }

    public static HarnessRunResult failed(
            String runId,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            List<HarnessStepResult> steps
    ) {
        return new HarnessRunResult(
                runId,
                HarnessRunStatus.FAILED,
                startedAt,
                finishedAt,
                steps,
                null,
                null,
                null,
                null,
                null
        );
    }
}