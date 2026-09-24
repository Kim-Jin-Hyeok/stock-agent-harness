package com.stock.harness;

import com.stock.agent.InvestmentDecision;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.risk.RiskCheckResult;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.TradeResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record HarnessRunResult(
        String runId,
        InvestmentStrategyIdentity strategyIdentity,
        HarnessRunStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<String> candidateSymbols,
        List<HarnessStepResult> steps,
        List<HarnessToolExecutionResult> toolResults,
        InvestmentDecision decision,
        RiskCheckResult riskCheckResult,
        TradeResult tradeResult,
        PortfolioSnapshot portfolioSnapshot,
        MarketSnapshot marketSnapshot
) {
    public HarnessRunResult {
        Objects.requireNonNull(strategyIdentity, "strategyIdentity must not be null.");
        candidateSymbols = List.copyOf(candidateSymbols);
        steps = List.copyOf(steps);
        toolResults = List.copyOf(toolResults);
    }

    public static HarnessRunResult of(
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            HarnessRunStatus status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            List<String> candidateSymbols,
            List<HarnessStepResult> steps,
            List<HarnessToolExecutionResult> toolResults,
            InvestmentDecision decision,
            RiskCheckResult riskCheckResult,
            TradeResult tradeResult,
            PortfolioSnapshot portfolioSnapshot,
            MarketSnapshot marketSnapshot
    ) {
        return new HarnessRunResult(
                runId,
                strategyIdentity,
                status,
                startedAt,
                finishedAt,
                candidateSymbols,
                steps,
                toolResults,
                decision,
                riskCheckResult,
                tradeResult,
                portfolioSnapshot,
                marketSnapshot
        );
    }

    public static HarnessRunResult of(
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            HarnessRunStatus status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            List<HarnessStepResult> steps,
            List<HarnessToolExecutionResult> toolResults,
            InvestmentDecision decision,
            RiskCheckResult riskCheckResult,
            TradeResult tradeResult,
            PortfolioSnapshot portfolioSnapshot,
            MarketSnapshot marketSnapshot
    ) {
        return of(
                runId,
                strategyIdentity,
                status,
                startedAt,
                finishedAt,
                List.of(),
                steps,
                toolResults,
                decision,
                riskCheckResult,
                tradeResult,
                portfolioSnapshot,
                marketSnapshot
        );
    }

    public static HarnessRunResult of(
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
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
        return of(
                runId,
                strategyIdentity,
                status,
                startedAt,
                finishedAt,
                List.of(),
                steps,
                List.of(),
                decision,
                riskCheckResult,
                tradeResult,
                portfolioSnapshot,
                marketSnapshot
        );
    }

    public static HarnessRunResult failed(
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            List<String> candidateSymbols,
            List<HarnessStepResult> steps,
            List<HarnessToolExecutionResult> toolResults
    ) {
        return new HarnessRunResult(
                runId,
                strategyIdentity,
                HarnessRunStatus.FAILED,
                startedAt,
                finishedAt,
                candidateSymbols,
                steps,
                toolResults,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static HarnessRunResult failed(
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            List<HarnessStepResult> steps,
            List<HarnessToolExecutionResult> toolResults
    ) {
        return failed(
                runId,
                strategyIdentity,
                startedAt,
                finishedAt,
                List.of(),
                steps,
                toolResults
        );
    }

    public static HarnessRunResult failed(
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            List<HarnessStepResult> steps
    ) {
        return failed(
                runId,
                strategyIdentity,
                startedAt,
                finishedAt,
                List.of(),
                steps,
                List.of()
        );
    }
}
