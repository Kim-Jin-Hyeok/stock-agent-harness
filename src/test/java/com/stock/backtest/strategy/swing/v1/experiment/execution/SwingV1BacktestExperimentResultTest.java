package com.stock.backtest.strategy.swing.v1.experiment.execution;

import com.stock.backtest.performance.benchmark.BacktestBenchmarkPerformanceSummary;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunRequest;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunResult;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReport;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private static final LocalDate FROM_VALUATION_DATE =
            LocalDate.of(2026, 1, 5);
    private static final LocalDate MIDDLE_VALUATION_DATE =
            LocalDate.of(2026, 1, 6);
    private static final LocalDate TO_VALUATION_DATE =
            LocalDate.of(2026, 7, 1);
    private static final long INITIAL_CASH_AMOUNT_KRW = 10_000_000L;

    @Test
    void copiesReportsAndPreservesPerSymbolConditions() {
        SwingV1BacktestExperimentRequest request = request("005930", "000660");
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
                        FROM_VALUATION_DATE,
                        TO_VALUATION_DATE
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
                        FROM_SIGNAL_DATE,
                        TO_SIGNAL_DATE
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Benchmark performance date range must match "
                                + "report valuation dates."
                );
    }

    @Test
    void rejectsReportsWithDifferentMiddleValuationDates() {
        SwingV1BacktestExperimentRequest request = request("005930", "000660");

        assertThatThrownBy(() -> new SwingV1BacktestExperimentResult(
                request,
                List.of(
                        report(
                                runRequest(
                                        request,
                                        "005930",
                                        BacktestPortfolioState.withCash(
                                                INITIAL_CASH_AMOUNT_KRW
                                        )
                                ),
                                FROM_VALUATION_DATE,
                                MIDDLE_VALUATION_DATE,
                                TO_VALUATION_DATE
                        ),
                        report(
                                runRequest(
                                        request,
                                        "000660",
                                        BacktestPortfolioState.withCash(
                                                INITIAL_CASH_AMOUNT_KRW
                                        )
                                ),
                                FROM_VALUATION_DATE,
                                TO_VALUATION_DATE
                        )
                ),
                benchmarkSummary()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Report valuation dates must match across symbols. "
                                + "symbol=000660, expectedDate="
                                + MIDDLE_VALUATION_DATE
                                + ", actualDate=" + TO_VALUATION_DATE
                );
    }

    @Test
    void rejectsBenchmarkPerformanceWithDifferentObservationCount() {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestRunRequest runRequest = runRequest(
                request,
                "005930",
                BacktestPortfolioState.withCash(INITIAL_CASH_AMOUNT_KRW)
        );
        SwingV1BacktestReport report = report(
                runRequest,
                FROM_VALUATION_DATE,
                MIDDLE_VALUATION_DATE,
                TO_VALUATION_DATE
        );

        assertThatThrownBy(() -> new SwingV1BacktestExperimentResult(
                request,
                List.of(report),
                benchmarkSummary()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Benchmark performance observation count must "
                                + "match report valuation dates."
                );
    }

    @Test
    void rejectsReportWithoutValuationSnapshots() {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestReport report = report(request, "005930");
        when(report.runResult().equityCurve()).thenReturn(List.of());

        assertThatThrownBy(() -> new SwingV1BacktestExperimentResult(
                request,
                List.of(report),
                benchmarkSummary()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Report equityCurve must not be empty.");
    }

    @Test
    void rejectsMissingExtraAndReplacedReportSymbols() {
        SwingV1BacktestExperimentRequest request = request("005930", "000660");
        SwingV1BacktestReport samsung = report(request, "005930");
        SwingV1BacktestReport skHynix = report(request, "000660");
        SwingV1BacktestReport unexpected = report(request, "035420");

        for (List<SwingV1BacktestReport> reports : List.of(
                List.of(samsung),
                List.of(samsung, skHynix, unexpected),
                List.of(samsung, unexpected)
        )) {
            assertThatThrownBy(() -> new SwingV1BacktestExperimentResult(
                    request,
                    reports,
                    benchmarkSummary()
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(
                            "Report symbols must exactly match requested "
                                    + "candidateSymbols in order."
                    );
        }
    }

    @Test
    void rejectsReorderedReportSymbols() {
        SwingV1BacktestExperimentRequest request = request("005930", "000660");

        assertThatThrownBy(() -> new SwingV1BacktestExperimentResult(
                request,
                List.of(report(request, "000660"), report(request, "005930")),
                benchmarkSummary()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Report symbols must exactly match requested "
                                + "candidateSymbols in order."
                );
    }

    private SwingV1BacktestExperimentRequest request() {
        return request("005930");
    }

    private SwingV1BacktestExperimentRequest request(String... symbols) {
        return new SwingV1BacktestExperimentRequest(
                STRATEGY_IDENTITY,
                List.of(symbols),
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
        return report(
                runRequest,
                FROM_VALUATION_DATE,
                TO_VALUATION_DATE
        );
    }

    private SwingV1BacktestReport report(
            SwingV1BacktestRunRequest runRequest,
            LocalDate... valuationDates
    ) {
        SwingV1BacktestReport report = mock(SwingV1BacktestReport.class);
        SwingV1BacktestRunResult runResult = mock(
                SwingV1BacktestRunResult.class
        );
        when(report.request()).thenReturn(runRequest);
        when(report.runResult()).thenReturn(runResult);
        when(runResult.equityCurve()).thenReturn(
                List.of(valuationDates).stream()
                        .map(this::equitySnapshot)
                        .toList()
        );
        return report;
    }

    private BacktestEquitySnapshot equitySnapshot(LocalDate valuationDate) {
        return new BacktestEquitySnapshot(
                valuationDate,
                valuationDate.atTime(9, 10)
                        .atZone(ZoneId.of("Asia/Seoul"))
                        .toInstant(),
                INITIAL_CASH_AMOUNT_KRW,
                0L,
                INITIAL_CASH_AMOUNT_KRW
        );
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
                FROM_VALUATION_DATE,
                TO_VALUATION_DATE
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
