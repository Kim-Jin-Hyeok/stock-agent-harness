package com.stock.backtest.strategy.swing.v1.experiment.execution;

import com.stock.backtest.performance.benchmark.BacktestBenchmarkPerformanceCalculator;
import com.stock.backtest.performance.benchmark.BacktestBenchmarkPerformanceSummary;
import com.stock.backtest.performance.benchmark.BacktestBenchmarkSeries;
import com.stock.backtest.performance.benchmark.query.BacktestBenchmarkSeriesQueryService;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunRequest;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunResult;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReport;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReportService;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.universe.StrategyStockUniverseRegistry;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SwingV1BacktestExperimentServiceTest {
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
    private static final LocalDate TO_VALUATION_DATE =
            LocalDate.of(2026, 7, 1);
    private static final long INITIAL_CASH_AMOUNT_KRW = 10_000_000L;

    private final StrategyStockUniverseRegistry stockUniverseRegistry =
            mock(StrategyStockUniverseRegistry.class);
    private final SwingV1BacktestReportService reportService =
            mock(SwingV1BacktestReportService.class);
    private final BacktestBenchmarkSeriesQueryService
            benchmarkSeriesQueryService = mock(
                    BacktestBenchmarkSeriesQueryService.class
            );
    private final BacktestBenchmarkPerformanceCalculator
            benchmarkPerformanceCalculator = mock(
                    BacktestBenchmarkPerformanceCalculator.class
            );
    private final BacktestBenchmarkSeries benchmarkSeries = mock(
            BacktestBenchmarkSeries.class
    );
    private final BacktestBenchmarkPerformanceSummary benchmarkSummary =
            benchmarkSummary();
    private final SwingV1BacktestExperimentService service =
            new SwingV1BacktestExperimentService(
                    stockUniverseRegistry,
                    reportService,
                    benchmarkSeriesQueryService,
                    benchmarkPerformanceCalculator
            );

    @BeforeEach
    void setUpBenchmark() {
        when(benchmarkSeriesQueryService.getSeries(
                BENCHMARK_ID,
                FROM_VALUATION_DATE,
                TO_VALUATION_DATE
        )).thenReturn(benchmarkSeries);
        when(benchmarkPerformanceCalculator.calculate(
                benchmarkSeries,
                FROM_VALUATION_DATE,
                TO_VALUATION_DATE
        )).thenReturn(benchmarkSummary);
    }

    @Test
    void executesEachUniverseSymbolWithIndependentEqualConditions() {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestRunRequest samsungRequest = runRequest(
                request,
                "005930"
        );
        SwingV1BacktestRunRequest skHynixRequest = runRequest(
                request,
                "000660"
        );
        SwingV1BacktestReport samsungReport = report(samsungRequest);
        SwingV1BacktestReport skHynixReport = report(skHynixRequest);
        when(stockUniverseRegistry.getCandidateSymbols(
                STRATEGY_IDENTITY
        )).thenReturn(List.of("005930", "000660"));
        when(reportService.generate(samsungRequest))
                .thenReturn(samsungReport);
        when(reportService.generate(skHynixRequest))
                .thenReturn(skHynixReport);

        SwingV1BacktestExperimentResult result = service.execute(request);

        assertThat(result.request()).isSameAs(request);
        assertThat(result.reports())
                .containsExactly(samsungReport, skHynixReport);
        assertThat(result.benchmarkPerformanceSummary())
                .isSameAs(benchmarkSummary);
        verify(benchmarkSeriesQueryService).getSeries(
                BENCHMARK_ID,
                FROM_VALUATION_DATE,
                TO_VALUATION_DATE
        );
        verify(benchmarkPerformanceCalculator).calculate(
                benchmarkSeries,
                FROM_VALUATION_DATE,
                TO_VALUATION_DATE
        );
        ArgumentCaptor<SwingV1BacktestRunRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        SwingV1BacktestRunRequest.class
                );
        verify(reportService, times(2)).generate(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(SwingV1BacktestRunRequest::candidateSymbol)
                .containsExactly("005930", "000660");
        assertThat(requestCaptor.getAllValues())
                .allSatisfy(runRequest -> {
                    assertThat(runRequest.strategyIdentity())
                            .isEqualTo(STRATEGY_IDENTITY);
                    assertThat(runRequest.fromSignalDate())
                            .isEqualTo(FROM_SIGNAL_DATE);
                    assertThat(runRequest.toSignalDate())
                            .isEqualTo(TO_SIGNAL_DATE);
                    assertThat(runRequest.initialPortfolioState())
                            .isEqualTo(BacktestPortfolioState.withCash(
                                    INITIAL_CASH_AMOUNT_KRW
                            ));
                    assertThat(runRequest.costModel())
                            .isSameAs(request.costModel());
                });
    }

    @Test
    void stopsAtFirstFailedSymbol() {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestRunRequest samsungRequest = runRequest(
                request,
                "005930"
        );
        SwingV1BacktestRunRequest skHynixRequest = runRequest(
                request,
                "000660"
        );
        SwingV1BacktestRunRequest naverRequest = runRequest(
                request,
                "035420"
        );
        SwingV1BacktestReport samsungReport = report(samsungRequest);
        when(stockUniverseRegistry.getCandidateSymbols(
                STRATEGY_IDENTITY
        )).thenReturn(List.of("005930", "000660", "035420"));
        when(reportService.generate(samsungRequest))
                .thenReturn(samsungReport);
        when(reportService.generate(skHynixRequest))
                .thenThrow(new IllegalStateException(
                        "Missing daily price history."
                ));

        assertThatThrownBy(() -> service.execute(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Missing daily price history.");
        verify(reportService).generate(samsungRequest);
        verify(reportService).generate(skHynixRequest);
        verify(reportService, never()).generate(naverRequest);
        verifyNoInteractions(
                benchmarkSeriesQueryService,
                benchmarkPerformanceCalculator
        );
    }

    @Test
    void rejectsDifferentValuationRangesBeforeLoadingBenchmark() {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestRunRequest samsungRequest = runRequest(
                request,
                "005930"
        );
        SwingV1BacktestRunRequest skHynixRequest = runRequest(
                request,
                "000660"
        );
        SwingV1BacktestReport samsungReport = report(samsungRequest);
        SwingV1BacktestReport skHynixReport = report(
                skHynixRequest,
                FROM_VALUATION_DATE.plusDays(1),
                TO_VALUATION_DATE
        );
        when(stockUniverseRegistry.getCandidateSymbols(
                STRATEGY_IDENTITY
        )).thenReturn(List.of("005930", "000660"));
        when(reportService.generate(samsungRequest))
                .thenReturn(samsungReport);
        when(reportService.generate(skHynixRequest))
                .thenReturn(skHynixReport);

        assertThatThrownBy(() -> service.execute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Report valuation date ranges must match "
                                + "across symbols."
                );
        verifyNoInteractions(
                benchmarkSeriesQueryService,
                benchmarkPerformanceCalculator
        );
    }

    @Test
    void rejectsEmptyUniverse() {
        SwingV1BacktestExperimentRequest request = request();
        when(stockUniverseRegistry.getCandidateSymbols(
                STRATEGY_IDENTITY
        )).thenReturn(List.of());

        assertThatThrownBy(() -> service.execute(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SWING_V1 stock universe must not be empty.");
        verify(reportService, never()).generate(
                org.mockito.ArgumentMatchers.any()
        );
        verifyNoInteractions(
                benchmarkSeriesQueryService,
                benchmarkPerformanceCalculator
        );
    }

    @Test
    void stopsAfterReportsWhenBenchmarkHistoryIsMissing() {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestRunRequest runRequest = runRequest(
                request,
                "005930"
        );
        SwingV1BacktestReport report = report(runRequest);
        when(stockUniverseRegistry.getCandidateSymbols(
                STRATEGY_IDENTITY
        )).thenReturn(List.of("005930"));
        when(reportService.generate(runRequest))
                .thenReturn(report);
        when(benchmarkSeriesQueryService.getSeries(
                BENCHMARK_ID,
                FROM_VALUATION_DATE,
                TO_VALUATION_DATE
        )).thenThrow(new IllegalStateException(
                "Stored benchmark history must not be empty."
        ));

        assertThatThrownBy(() -> service.execute(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stored benchmark history must not be empty.");
        verify(reportService).generate(runRequest);
        verifyNoInteractions(benchmarkPerformanceCalculator);
    }

    @Test
    void stopsWhenBenchmarkHasNoFinalValuationDate() {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestRunRequest runRequest = runRequest(
                request,
                "005930"
        );
        SwingV1BacktestReport report = report(runRequest);
        when(stockUniverseRegistry.getCandidateSymbols(
                STRATEGY_IDENTITY
        )).thenReturn(List.of("005930"));
        when(reportService.generate(runRequest))
                .thenReturn(report);
        when(benchmarkPerformanceCalculator.calculate(
                benchmarkSeries,
                FROM_VALUATION_DATE,
                TO_VALUATION_DATE
        )).thenThrow(new IllegalArgumentException(
                "Benchmark observation not found for toDate: "
                        + TO_VALUATION_DATE
        ));

        assertThatThrownBy(() -> service.execute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Benchmark observation not found for toDate: "
                                + TO_VALUATION_DATE
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

    private SwingV1BacktestRunRequest runRequest(
            SwingV1BacktestExperimentRequest request,
            String symbol
    ) {
        return new SwingV1BacktestRunRequest(
                request.strategyIdentity(),
                symbol,
                request.fromSignalDate(),
                request.toSignalDate(),
                BacktestPortfolioState.withCash(
                        request.initialCashAmountKrwPerSymbol()
                ),
                request.costModel()
        );
    }

    private SwingV1BacktestReport report(
            SwingV1BacktestRunRequest request
    ) {
        return report(
                request,
                FROM_VALUATION_DATE,
                TO_VALUATION_DATE
        );
    }

    private SwingV1BacktestReport report(
            SwingV1BacktestRunRequest request,
            LocalDate fromValuationDate,
            LocalDate toValuationDate
    ) {
        SwingV1BacktestReport report = mock(SwingV1BacktestReport.class);
        SwingV1BacktestRunResult runResult = mock(
                SwingV1BacktestRunResult.class
        );
        when(report.request()).thenReturn(request);
        when(report.runResult()).thenReturn(runResult);
        when(runResult.equityCurve()).thenReturn(List.of(
                equitySnapshot(fromValuationDate),
                equitySnapshot(toValuationDate)
        ));
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
        return new BacktestBenchmarkPerformanceSummary(
                BENCHMARK_ID,
                FROM_VALUATION_DATE,
                TO_VALUATION_DATE,
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
