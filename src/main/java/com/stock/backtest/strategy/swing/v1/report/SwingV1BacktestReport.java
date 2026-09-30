package com.stock.backtest.strategy.swing.v1.report;

import com.stock.agent.InvestmentAction;
import com.stock.backtest.execution.fill.daily.DailyOpenFillApproximation;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.performance.metric.BacktestPerformanceSummary;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepStatus;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunRequest;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunResult;
import com.stock.backtest.strategy.swing.v1.report.terminal.SwingV1TerminalLiquidationEstimate;
import com.stock.backtest.strategy.swing.v1.report.trade.SwingV1CompletedTrade;

import java.util.List;
import java.util.Objects;

public record SwingV1BacktestReport(
        SwingV1BacktestRunRequest request,
        SwingV1BacktestRunResult runResult,
        BacktestPerformanceSummary performanceSummary,
        SwingV1TerminalLiquidationEstimate terminalLiquidationEstimate,
        List<SwingV1CompletedTrade> completedTrades
) {
    public SwingV1BacktestReport {
        Objects.requireNonNull(request, "request must not be null.");
        Objects.requireNonNull(
                runResult,
                "runResult must not be null."
        );
        Objects.requireNonNull(
                performanceSummary,
                "performanceSummary must not be null."
        );
        Objects.requireNonNull(
                terminalLiquidationEstimate,
                "terminalLiquidationEstimate must not be null."
        );
        completedTrades = List.copyOf(Objects.requireNonNull(
                completedTrades,
                "completedTrades must not be null."
        ));

        validateRunMatchesRequest(request, runResult);
        validatePerformanceSummary(runResult, performanceSummary);
        validateTerminalLiquidationEstimate(
                performanceSummary,
                terminalLiquidationEstimate
        );
        validateCompletedTrades(request, runResult, completedTrades);
    }

    private static void validateRunMatchesRequest(
            SwingV1BacktestRunRequest request,
            SwingV1BacktestRunResult runResult
    ) {
        if (!request.strategyIdentity().equals(
                runResult.strategyIdentity()
        )) {
            throw new IllegalArgumentException(
                    "runResult strategyIdentity must match request."
            );
        }
        if (!request.candidateSymbol().equals(
                runResult.candidateSymbol()
        )) {
            throw new IllegalArgumentException(
                    "runResult candidateSymbol must match request."
            );
        }
        if (!request.fromSignalDate().equals(
                runResult.fromSignalDate()
        ) || !request.toSignalDate().equals(
                runResult.toSignalDate()
        )) {
            throw new IllegalArgumentException(
                    "runResult signal date range must match request."
            );
        }
        if (!request.initialPortfolioState().equals(
                runResult.initialPortfolioState()
        )) {
            throw new IllegalArgumentException(
                    "runResult initialPortfolioState must match request."
            );
        }
    }

    private static void validatePerformanceSummary(
            SwingV1BacktestRunResult runResult,
            BacktestPerformanceSummary performanceSummary
    ) {
        List<BacktestEquitySnapshot> equityCurve =
                runResult.equityCurve();
        if (equityCurve.isEmpty()) {
            throw new IllegalArgumentException(
                    "runResult equityCurve must not be empty."
            );
        }
        if (performanceSummary.initialEquityAmountKrw()
                != runResult.initialPortfolioState().cashAmountKrw()) {
            throw new IllegalArgumentException(
                    "performanceSummary initial equity must match runResult."
            );
        }
        if (performanceSummary.finalEquityAmountKrw()
                != equityCurve.getLast().totalAssetAmountKrw()) {
            throw new IllegalArgumentException(
                    "performanceSummary final equity must match runResult."
            );
        }
        if (performanceSummary.observationCount()
                != equityCurve.size()) {
            throw new IllegalArgumentException(
                    "performanceSummary observation count must match "
                            + "runResult."
            );
        }
    }

    private static void validateTerminalLiquidationEstimate(
            BacktestPerformanceSummary performanceSummary,
            SwingV1TerminalLiquidationEstimate terminalEstimate
    ) {
        if (terminalEstimate.initialEquityAmountKrw()
                != performanceSummary.initialEquityAmountKrw()) {
            throw new IllegalArgumentException(
                    "terminalLiquidationEstimate initial equity must match "
                            + "performanceSummary."
            );
        }
        if (terminalEstimate.markToMarketFinalEquityAmountKrw()
                != performanceSummary.finalEquityAmountKrw()) {
            throw new IllegalArgumentException(
                    "terminalLiquidationEstimate mark-to-market equity "
                            + "must match performanceSummary."
            );
        }
    }

    private static void validateCompletedTrades(
            SwingV1BacktestRunRequest request,
            SwingV1BacktestRunResult runResult,
            List<SwingV1CompletedTrade> completedTrades
    ) {
        List<DailyOpenFillApproximation> executedFills = runResult.steps()
                .stream()
                .filter(step -> step.status()
                        == SwingV1BacktestStepStatus.EXECUTED)
                .map(step -> step.fill())
                .toList();
        long executedSellCount = executedFills.stream()
                .filter(fill -> fill.tradeCostCalculation().action()
                        == InvestmentAction.SELL)
                .count();
        if (completedTrades.size() != executedSellCount) {
            throw new IllegalArgumentException(
                    "completedTrades count must match executed SELL count."
            );
        }

        for (SwingV1CompletedTrade completedTrade : completedTrades) {
            Objects.requireNonNull(
                    completedTrade,
                    "completedTrade must not be null."
            );
            if (!request.candidateSymbol().equals(
                    completedTrade.symbol()
            )) {
                throw new IllegalArgumentException(
                        "completedTrade symbol must match request candidate."
                );
            }
            if (!request.costModel().equals(completedTrade.entryFill()
                    .tradeCostCalculation().costModel())
                    || !request.costModel().equals(completedTrade.exitFill()
                    .tradeCostCalculation().costModel())) {
                throw new IllegalArgumentException(
                        "completedTrade cost model must match request."
                );
            }
            if (!executedFills.contains(completedTrade.entryFill())
                    || !executedFills.contains(completedTrade.exitFill())) {
                throw new IllegalArgumentException(
                        "completedTrade fills must belong to runResult."
                );
            }
        }
    }
}
