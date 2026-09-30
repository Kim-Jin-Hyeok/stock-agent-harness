package com.stock.backtest.strategy.swing.v1.report;

import com.stock.backtest.performance.metric.BacktestPerformanceCalculator;
import com.stock.backtest.performance.metric.BacktestPerformanceSummary;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunRequest;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunResult;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunService;
import com.stock.backtest.strategy.swing.v1.report.terminal.SwingV1TerminalLiquidationCalculator;
import com.stock.backtest.strategy.swing.v1.report.terminal.SwingV1TerminalLiquidationEstimate;
import com.stock.backtest.strategy.swing.v1.report.trade.SwingV1CompletedTrade;
import com.stock.backtest.strategy.swing.v1.report.trade.SwingV1CompletedTradeExtractor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class SwingV1BacktestReportService {
    private final SwingV1BacktestRunService runService;
    private final BacktestPerformanceCalculator performanceCalculator;
    private final SwingV1TerminalLiquidationCalculator
            terminalLiquidationCalculator;
    private final SwingV1CompletedTradeExtractor completedTradeExtractor;

    public SwingV1BacktestReportService(
            SwingV1BacktestRunService runService,
            BacktestPerformanceCalculator performanceCalculator,
            SwingV1TerminalLiquidationCalculator
                    terminalLiquidationCalculator,
            SwingV1CompletedTradeExtractor completedTradeExtractor
    ) {
        this.runService = Objects.requireNonNull(
                runService,
                "runService must not be null."
        );
        this.performanceCalculator = Objects.requireNonNull(
                performanceCalculator,
                "performanceCalculator must not be null."
        );
        this.terminalLiquidationCalculator = Objects.requireNonNull(
                terminalLiquidationCalculator,
                "terminalLiquidationCalculator must not be null."
        );
        this.completedTradeExtractor = Objects.requireNonNull(
                completedTradeExtractor,
                "completedTradeExtractor must not be null."
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
        SwingV1TerminalLiquidationEstimate terminalEstimate =
                terminalLiquidationCalculator.calculate(
                        performanceSummary.initialEquityAmountKrw(),
                        runResult.finalPortfolioState(),
                        runResult.equityCurve().getLast(),
                        request.costModel()
                );
        List<SwingV1CompletedTrade> completedTrades =
                completedTradeExtractor.extract(runResult.steps());
        return new SwingV1BacktestReport(
                request,
                runResult,
                performanceSummary,
                terminalEstimate,
                completedTrades
        );
    }
}
