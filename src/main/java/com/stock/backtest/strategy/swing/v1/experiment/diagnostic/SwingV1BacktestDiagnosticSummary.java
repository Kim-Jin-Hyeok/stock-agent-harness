package com.stock.backtest.strategy.swing.v1.experiment.diagnostic;

import com.stock.agent.InvestmentAction;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityReasonCode;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionReasonCode;
import com.stock.agent.evidence.swing.v1.SwingV1DecisionEvidence;
import com.stock.backtest.portfolio.BacktestPosition;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionReasonCode;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepResult;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepStatus;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunResult;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record SwingV1BacktestDiagnosticSummary(
        String symbol,
        Map<SwingV1BacktestStepStatus, Long> stepStatusCounts,
        Map<SwingV1ActionReasonCode, Long> actionReasonCounts,
        Map<SwingV1OrderQuantityReasonCode, Long> blockedOrderReasonCounts,
        Map<BacktestPortfolioTransitionReasonCode, Long> rejectedTransitionReasonCounts,
        long executedBuyCount,
        long executedSellCount,
        long finalPositionQuantity
) {
    public SwingV1BacktestDiagnosticSummary {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        stepStatusCounts = immutableCounts(
                stepStatusCounts,
                SwingV1BacktestStepStatus.class
        );
        actionReasonCounts = immutableCounts(
                actionReasonCounts,
                SwingV1ActionReasonCode.class
        );
        blockedOrderReasonCounts = immutableCounts(
                blockedOrderReasonCounts,
                SwingV1OrderQuantityReasonCode.class
        );
        rejectedTransitionReasonCounts = immutableCounts(
                rejectedTransitionReasonCounts,
                BacktestPortfolioTransitionReasonCode.class
        );
        if (executedBuyCount < 0 || executedSellCount < 0
                || finalPositionQuantity < 0) {
            throw new IllegalArgumentException(
                    "Diagnostic counts and final position quantity must not be negative."
            );
        }
    }

    public static SwingV1BacktestDiagnosticSummary from(
            SwingV1BacktestRunResult runResult
    ) {
        Objects.requireNonNull(runResult, "runResult must not be null.");
        Map<SwingV1BacktestStepStatus, Long> statusCounts =
                new EnumMap<>(SwingV1BacktestStepStatus.class);
        Map<SwingV1ActionReasonCode, Long> actionCounts =
                new EnumMap<>(SwingV1ActionReasonCode.class);
        Map<SwingV1OrderQuantityReasonCode, Long> blockedCounts =
                new EnumMap<>(SwingV1OrderQuantityReasonCode.class);
        Map<BacktestPortfolioTransitionReasonCode, Long> rejectedCounts =
                new EnumMap<>(BacktestPortfolioTransitionReasonCode.class);
        long buyCount = 0;
        long sellCount = 0;

        for (SwingV1BacktestStepResult step : runResult.steps()) {
            statusCounts.merge(step.status(), 1L, Math::addExact);
            if (step.status() == SwingV1BacktestStepStatus.NO_NEXT_DAILY_BAR) {
                continue;
            }

            SwingV1DecisionEvidence evidence = Objects.requireNonNull(
                    step.decision().swingV1Evidence(),
                    "SWING_V1 step decision evidence must not be null."
            );
            actionCounts.merge(
                    evidence.actionPolicyResult().reasonCode(),
                    1L,
                    Math::addExact
            );
            if (step.decision().action() == InvestmentAction.HOLD
                    && evidence.actionPolicyResult().action()
                    != InvestmentAction.HOLD) {
                blockedCounts.merge(
                        evidence.orderQuantityResult().reasonCode(),
                        1L,
                        Math::addExact
                );
            }
            if (step.status() == SwingV1BacktestStepStatus.REJECTED) {
                rejectedCounts.merge(
                        step.portfolioTransition().reasonCode(),
                        1L,
                        Math::addExact
                );
            }
            if (step.status() == SwingV1BacktestStepStatus.EXECUTED) {
                if (step.decision().action() == InvestmentAction.BUY) {
                    buyCount = Math.addExact(buyCount, 1);
                } else if (step.decision().action() == InvestmentAction.SELL) {
                    sellCount = Math.addExact(sellCount, 1);
                }
            }
        }

        long positionQuantity = runResult.finalPortfolioState()
                .findPosition(runResult.candidateSymbol())
                .map(BacktestPosition::quantity)
                .orElse(0L);
        return new SwingV1BacktestDiagnosticSummary(
                runResult.candidateSymbol(),
                statusCounts,
                actionCounts,
                blockedCounts,
                rejectedCounts,
                buyCount,
                sellCount,
                positionQuantity
        );
    }

    private static <E extends Enum<E>> Map<E, Long> immutableCounts(
            Map<E, Long> counts,
            Class<E> enumType
    ) {
        Map<E, Long> copy = new EnumMap<>(enumType);
        copy.putAll(Objects.requireNonNull(counts, "counts must not be null."));
        return Collections.unmodifiableMap(copy);
    }
}
