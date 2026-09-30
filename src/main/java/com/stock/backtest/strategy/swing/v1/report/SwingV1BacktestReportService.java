package com.stock.backtest.strategy.swing.v1.report;

import com.stock.backtest.performance.metric.BacktestPerformanceCalculator;
import com.stock.backtest.performance.metric.BacktestPerformanceSummary;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunRequest;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunResult;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunService;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class SwingV1BacktestReportService {
    private final SwingV1BacktestRunService runService;
    private final BacktestPerformanceCalculator performanceCalculator;

    public SwingV1BacktestReportService(
            SwingV1BacktestRunService runService,
            BacktestPerformanceCalculator performanceCalculator
    ) {
        this.runService = Objects.requireNonNull(
                runService,
                "runService must not be null."
        );
        this.performanceCalculator = Objects.requireNonNull(
                performanceCalculator,
                "performanceCalculator must not be null."
        );
    }

    public SwingV1BacktestReport generate(
            SwingV1BacktestRunRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        if (!request.initialPortfolioState().positions().isEmpty()) {
            throw new IllegalArgumentException(
                    "initialPortfolioState must not contain positions for "
                            + "cash-start performance calculation."
            );
        }

        SwingV1BacktestRunResult runResult = runService.execute(request);
        BacktestPerformanceSummary performanceSummary =
                performanceCalculator.calculate(
                        runResult.initialPortfolioState(),
                        runResult.equityCurve()
                );
        return new SwingV1BacktestReport(
                request,
                runResult,
                performanceSummary
        );
    }
}
