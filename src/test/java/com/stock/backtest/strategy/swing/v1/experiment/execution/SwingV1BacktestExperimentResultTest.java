package com.stock.backtest.strategy.swing.v1.experiment.execution;

import com.stock.backtest.performance.benchmark.BacktestBenchmarkPerformanceSummary;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunRequest;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReport;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwingV1BacktestExperimentResultTest {
    private static final String BENCHMARK_ID = "KOSPI";
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );
    private static final LocalDate FROM_SIGNAL_DATE =
            LocalDate.of(2026, 1, 2);
    private static final LocalDate TO_SIGNAL_DATE =
            LocalDate.of(2026, 6, 30);
    private static final long INITIAL_CASH_AMOUNT_KRW = 10_000_000L;

    @Test
    void copiesReportsAndPreservesPerSymbolConditions() {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestReport samsung = report(request, "005930");
        SwingV1BacktestReport skHynix = report(request, "000660");
        List<SwingV1BacktestReport> mutableReports = new ArrayList<>(
                List.of(samsung, skHynix)
        );
        BacktestBenchmarkPerformanceSummary benchmarkSummary =
                benchmarkSummary();

        SwingV1BacktestExperimentResult result =
                new SwingV1BacktestExperimentResult(
                        request,
                        mutableReports,
                        benchmarkSummary
                );
        mutableReports.clear();

        assertThat(result.request()).isSameAs(request);
        assertThat(result.reports()).containsExactly(samsung, skHynix);
        assertThat(result.benchmarkPerformanceSummary())
                .isSameAs(benchmarkSummary);
        assertThatThrownBy(() -> result.reports().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateReportSymbols() {
        SwingV1BacktestExperimentRequest request = request();

        assertThatThrownBy(() -> new SwingV1BacktestExperimentResult(
                request,
                List.of(
                        report(request, "005930"),
                        report(request, "005930")
                ),
                benchmarkSummary()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Reports must not contain duplicate symbol: 005930"
                );
    }

    @Test
    void rejectsReportWithDifferentInitialCash() {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestRunRequest mismatchedRunRequest =
                runRequest(
                        request,
                        "005930",
                        BacktestPortfolioState.withCash(5_000_000L)
                );

        assertThatThrownBy(() -> new SwingV1BacktestExperimentResult(
                request,
                List.of(report(mismatchedRunRequest)),
                benchmarkSummary()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Report initial portfolio must match per-symbol "
                                + "experiment cash."
                );
    }

    @Test
    void rejectsEmptyReports() {
        assertThatThrownBy(() -> new SwingV1BacktestExperimentResult(
                request(),
                List.of(),
                benchmarkSummary()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reports must not be empty.");
    }

    @Test
    void rejectsNullBenchmarkPerformanceSummary() {
        SwingV1BacktestExperimentRequest request = request();

        assertThatThrownBy(() -> new SwingV1BacktestExperimentResult(
                request,
                List.of(report(request, "005930")),
                null
        )).isInstanceOf(NullPointerException.class)
                .hasMessage(
                        "benchmarkPerformanceSummary must not be null."
                );
    }

    @Test
    void rejectsBenchmarkPerformanceForDifferentBenchmark() {
        SwingV1BacktestExperimentRequest request = request();

        assertThatThrownBy(() -> new SwingV1BacktestExperimentResult(
                request,
                List.of(report(request, "005930")),
                benchmarkSummary(
                        "KOSDAQ",
                        FROM_SIGNAL_DATE,
                        TO_SIGNAL_DATE
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Benchmark performance benchmarkId must match "
                                + "experiment request."
                );
    }

    @Test
    void rejectsBenchmarkPerformanceForDifferentDateRange() {
        SwingV1BacktestExperimentRequest request = request();

        assertThatThrownBy(() -> new SwingV1BacktestExperimentResult(
                request,
                List.of(report(request, "005930")),
                benchmarkSummary(
                        BENCHMARK_ID,
                        FROM_SIGNAL_DATE.plusDays(1),
                        TO_SIGNAL_DATE
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Benchmark performance date range must match "
                                + "experiment request."
                );
    }

    private SwingV1BacktestExperimentRequest request() {
        return new SwingV1BacktestExperimentRequest(
                STRATEGY_IDENTITY,
                BENCHMARK_ID,
                FROM_SIGNAL_DATE,
                TO_SIGNAL_DATE,
                INITIAL_CASH_AMOUNT_KRW,
                costModel()
        );
    }

    private SwingV1BacktestReport report(
            SwingV1BacktestExperimentRequest request,
            String symbol
    ) {
        return report(runRequest(
                request,
                symbol,
                BacktestPortfolioState.withCash(
                        request.initialCashAmountKrwPerSymbol()
                )
        ));
    }

    private SwingV1BacktestReport report(
            SwingV1BacktestRunRequest runRequest
    ) {
        SwingV1BacktestReport report = mock(SwingV1BacktestReport.class);
        when(report.request()).thenReturn(runRequest);
        return report;
    }

    private SwingV1BacktestRunRequest runRequest(
            SwingV1BacktestExperimentRequest request,
            String symbol,
            BacktestPortfolioState initialPortfolioState
    ) {
        return new SwingV1BacktestRunRequest(
                request.strategyIdentity(),
                symbol,
                request.fromSignalDate(),
                request.toSignalDate(),
                initialPortfolioState,
                request.costModel()
        );
    }

    private TradeCostModel costModel() {
        return new TradeCostModel(
                "KIS_SIMULATION_V1",
                1,
                new BigDecimal("0.00015"),
                new BigDecimal("0.00015"),
                new BigDecimal("0.0018"),
                new BigDecimal("0.0010"),
                new BigDecimal("0.0010")
        );
    }

    private BacktestBenchmarkPerformanceSummary benchmarkSummary() {
        return benchmarkSummary(
                BENCHMARK_ID,
                FROM_SIGNAL_DATE,
                TO_SIGNAL_DATE
        );
    }

    private BacktestBenchmarkPerformanceSummary benchmarkSummary(
            String benchmarkId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return new BacktestBenchmarkPerformanceSummary(
                benchmarkId,
                fromDate,
                toDate,
                new BigDecimal("100"),
                new BigDecimal("110"),
                new BigDecimal("0.1"),
                BigDecimal.ZERO,
                null,
                null,
                2
        );
    }
}
