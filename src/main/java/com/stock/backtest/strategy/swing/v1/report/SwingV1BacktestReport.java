package com.stock.backtest.strategy.swing.v1.report;

import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.performance.metric.BacktestPerformanceSummary;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunRequest;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunResult;

import java.util.List;
import java.util.Objects;

public record SwingV1BacktestReport(
        SwingV1BacktestRunRequest request,
        SwingV1BacktestRunResult runResult,
        BacktestPerformanceSummary performanceSummary
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

        validateRunMatchesRequest(request, runResult);
        validatePerformanceSummary(runResult, performanceSummary);
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
}
