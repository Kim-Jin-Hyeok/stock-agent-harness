package com.stock.backtest.strategy.swing.v1.experiment.execution;

import com.stock.backtest.performance.benchmark.BacktestBenchmarkPerformanceSummary;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunRequest;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReport;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record SwingV1BacktestExperimentResult(
        SwingV1BacktestExperimentRequest request,
        List<SwingV1BacktestReport> reports,
        BacktestBenchmarkPerformanceSummary benchmarkPerformanceSummary
) {
    public SwingV1BacktestExperimentResult {
        Objects.requireNonNull(request, "request must not be null.");
        reports = List.copyOf(Objects.requireNonNull(
                reports,
                "reports must not be null."
        ));
        if (reports.isEmpty()) {
            throw new IllegalArgumentException(
                    "reports must not be empty."
            );
        }
        Objects.requireNonNull(
                benchmarkPerformanceSummary,
                "benchmarkPerformanceSummary must not be null."
        );
        validateReports(request, reports);
        validateBenchmarkPerformanceSummary(
                request,
                SwingV1BacktestValuationRange.fromReports(reports),
                benchmarkPerformanceSummary
        );
    }

    private static void validateBenchmarkPerformanceSummary(
            SwingV1BacktestExperimentRequest request,
            SwingV1BacktestValuationRange valuationRange,
            BacktestBenchmarkPerformanceSummary summary
    ) {
        if (!request.benchmarkId().equals(summary.benchmarkId())) {
            throw new IllegalArgumentException(
                    "Benchmark performance benchmarkId must match "
                            + "experiment request."
            );
        }
        if (!valuationRange.fromDate().equals(summary.fromDate())
                || !valuationRange.toDate().equals(summary.toDate())) {
            throw new IllegalArgumentException(
                    "Benchmark performance date range must match "
                            + "report valuation dates."
            );
        }
    }

    private static void validateReports(
            SwingV1BacktestExperimentRequest experimentRequest,
            List<SwingV1BacktestReport> reports
    ) {
        Set<String> symbols = new HashSet<>();
        BacktestPortfolioState expectedInitialPortfolio =
                BacktestPortfolioState.withCash(
                        experimentRequest.initialCashAmountKrwPerSymbol()
                );

        for (SwingV1BacktestReport report : reports) {
            Objects.requireNonNull(report, "report must not be null.");
            SwingV1BacktestRunRequest runRequest = report.request();
            if (!experimentRequest.strategyIdentity().equals(
                    runRequest.strategyIdentity()
            )) {
                throw new IllegalArgumentException(
                        "Report strategyIdentity must match experiment."
                );
            }
            if (!experimentRequest.fromSignalDate().equals(
                    runRequest.fromSignalDate()
            ) || !experimentRequest.toSignalDate().equals(
                    runRequest.toSignalDate()
            )) {
                throw new IllegalArgumentException(
                        "Report signal date range must match experiment."
                );
            }
            if (!expectedInitialPortfolio.equals(
                    runRequest.initialPortfolioState()
            )) {
                throw new IllegalArgumentException(
                        "Report initial portfolio must match per-symbol "
                                + "experiment cash."
                );
            }
            if (!experimentRequest.costModel().equals(
                    runRequest.costModel()
            )) {
                throw new IllegalArgumentException(
                        "Report costModel must match experiment."
                );
            }
            if (!symbols.add(runRequest.candidateSymbol())) {
                throw new IllegalArgumentException(
                        "Reports must not contain duplicate symbol: "
                                + runRequest.candidateSymbol()
                );
            }
        }
    }
}
