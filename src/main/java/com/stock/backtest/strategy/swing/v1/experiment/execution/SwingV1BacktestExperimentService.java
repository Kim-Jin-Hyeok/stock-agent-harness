package com.stock.backtest.strategy.swing.v1.experiment.execution;

import com.stock.backtest.performance.benchmark.BacktestBenchmarkPerformanceCalculator;
import com.stock.backtest.performance.benchmark.BacktestBenchmarkPerformanceSummary;
import com.stock.backtest.performance.benchmark.BacktestBenchmarkSeries;
import com.stock.backtest.performance.benchmark.query.BacktestBenchmarkSeriesQueryService;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunRequest;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReport;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReportService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class SwingV1BacktestExperimentService {
    private final SwingV1BacktestReportService reportService;
    private final BacktestBenchmarkSeriesQueryService benchmarkSeriesQueryService;
    private final BacktestBenchmarkPerformanceCalculator
            benchmarkPerformanceCalculator;

    public SwingV1BacktestExperimentService(
            SwingV1BacktestReportService reportService,
            BacktestBenchmarkSeriesQueryService benchmarkSeriesQueryService,
            BacktestBenchmarkPerformanceCalculator benchmarkPerformanceCalculator
    ) {
        this.reportService = Objects.requireNonNull(
                reportService,
                "reportService must not be null."
        );
        this.benchmarkSeriesQueryService = Objects.requireNonNull(
                benchmarkSeriesQueryService,
                "benchmarkSeriesQueryService must not be null."
        );
        this.benchmarkPerformanceCalculator = Objects.requireNonNull(
                benchmarkPerformanceCalculator,
                "benchmarkPerformanceCalculator must not be null."
        );
    }

    public SwingV1BacktestExperimentResult execute(
            SwingV1BacktestExperimentRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        List<String> candidateSymbols = request.candidateSymbols();

        List<SwingV1BacktestReport> reports = new ArrayList<>(
                candidateSymbols.size()
        );
        for (String candidateSymbol : candidateSymbols) {
            SwingV1BacktestRunRequest runRequest =
                    new SwingV1BacktestRunRequest(
                            request.strategyIdentity(),
                            candidateSymbol,
                            request.fromSignalDate(),
                            request.toSignalDate(),
                            BacktestPortfolioState.withCash(
                                    request.initialCashAmountKrwPerSymbol()
                            ),
                            request.costModel()
                    );
            SwingV1BacktestReport report = reportService.generate(runRequest);
            if (!candidateSymbol.equals(report.request().candidateSymbol())) {
                throw new IllegalArgumentException(
                        "Generated report symbol must match requested candidate. "
                                + "expected=" + candidateSymbol
                                + ", actual=" + report.request().candidateSymbol()
                );
            }
            reports.add(report);
        }

        SwingV1BacktestValuationDates valuationDates =
                SwingV1BacktestValuationDates.fromReports(reports);
        BacktestBenchmarkSeries benchmarkSeries = benchmarkSeriesQueryService
                .getSeries(
                        request.benchmarkId(),
                        valuationDates.fromDate(),
                        valuationDates.toDate()
                );
        valuationDates.validateBenchmarkDates(benchmarkSeries);
        BacktestBenchmarkPerformanceSummary benchmarkPerformanceSummary =
                benchmarkPerformanceCalculator.calculate(
                        benchmarkSeries,
                        valuationDates.fromDate(),
                        valuationDates.toDate()
                );

        return new SwingV1BacktestExperimentResult(
                request,
                reports,
                benchmarkPerformanceSummary
        );
    }
}
