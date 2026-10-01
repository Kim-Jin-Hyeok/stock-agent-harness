package com.stock.backtest.strategy.swing.v1.experiment.execution;

import com.stock.backtest.performance.benchmark.BacktestBenchmarkObservation;
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
    private static final LocalDate MIDDLE_VALUATION_DATE =
            LocalDate.of(2026, 1, 6);
    private static final LocalDate TO_VALUATION_DATE =
            LocalDate.of(2026, 7, 1);
    private static final long INITIAL_CASH_AMOUNT_KRW = 10_000_000L;

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
    private final BacktestBenchmarkSeries benchmarkSeries = benchmarkSeries(
            FROM_VALUATION_DATE,
            MIDDLE_VALUATION_DATE,
            TO_VALUATION_DATE
    );
    private final BacktestBenchmarkPerformanceSummary benchmarkSummary =
            benchmarkSummary();
    private final SwingV1BacktestExperimentService service =
            new SwingV1BacktestExperimentService(
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
    void executesEachRequestedSymbolWithIndependentEqualConditions() {
        SwingV1BacktestExperimentRequest request = request("005930", "000660");
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
        SwingV1BacktestExperimentRequest request = request("005930", "000660", "035420");
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
    void rejectsMissingMiddleValuationDateBeforeLoadingBenchmark() {
        SwingV1BacktestExperimentRequest request = request("005930", "000660");
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
                FROM_VALUATION_DATE,
                TO_VALUATION_DATE
        );
        when(reportService.generate(samsungRequest))
                .thenReturn(samsungReport);
        when(reportService.generate(skHynixRequest))
                .thenReturn(skHynixReport);

        assertThatThrownBy(() -> service.execute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Report valuation dates must match across symbols. "
                                + "symbol=000660, expectedDate="
                                + MIDDLE_VALUATION_DATE
                                + ", actualDate=" + TO_VALUATION_DATE
                );
        verifyNoInteractions(
                benchmarkSeriesQueryService,
                benchmarkPerformanceCalculator
        );
    }

    @Test
    void rejectsNullRequestWithoutLoadingReportsOrBenchmark() {
        assertThatThrownBy(() -> service.execute(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request must not be null.");
        verifyNoInteractions(
                reportService,
                benchmarkSeriesQueryService,
                benchmarkPerformanceCalculator
        );
    }

    @Test
    void stopsAfterReportsWhenBenchmarkHistoryIsMissing() {
        SwingV1BacktestExperimentRequest request = request("005930");
        SwingV1BacktestRunRequest runRequest = runRequest(
                request,
                "005930"
        );
        SwingV1BacktestReport report = report(runRequest);
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
    void stopsWhenBenchmarkHasNoMiddleValuationDate() {
        SwingV1BacktestExperimentRequest request = request("005930");
        SwingV1BacktestRunRequest runRequest = runRequest(
                request,
                "005930"
        );
        SwingV1BacktestReport report = report(runRequest);
        when(reportService.generate(runRequest))
                .thenReturn(report);
        when(benchmarkSeriesQueryService.getSeries(
                BENCHMARK_ID,
                FROM_VALUATION_DATE,
                TO_VALUATION_DATE
        )).thenReturn(benchmarkSeries(
                FROM_VALUATION_DATE,
                TO_VALUATION_DATE
        ));

        assertThatThrownBy(() -> service.execute(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Benchmark observation dates must match report "
                                + "valuation dates. benchmarkId=KOSPI, "
                                + "expectedDate=" + MIDDLE_VALUATION_DATE
                                + ", actualDate=" + TO_VALUATION_DATE
                );
        verifyNoInteractions(benchmarkPerformanceCalculator);
    }

    @Test
    void stopsWhenBenchmarkHasExtraValuationDate() {
        SwingV1BacktestExperimentRequest request = request("005930");
        SwingV1BacktestRunRequest runRequest = runRequest(request, "005930");
        SwingV1BacktestReport report = report(runRequest);
        when(reportService.generate(runRequest)).thenReturn(report);
        when(benchmarkSeriesQueryService.getSeries(
                BENCHMARK_ID,
                FROM_VALUATION_DATE,
                TO_VALUATION_DATE
        )).thenReturn(benchmarkSeries(
                FROM_VALUATION_DATE,
                MIDDLE_VALUATION_DATE,
                MIDDLE_VALUATION_DATE.plusDays(1),
                TO_VALUATION_DATE
        ));

        assertThatThrownBy(() -> service.execute(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Benchmark observation dates must match report "
                                + "valuation dates. benchmarkId=KOSPI, "
                                + "expectedDate=" + TO_VALUATION_DATE
                                + ", actualDate="
                                + MIDDLE_VALUATION_DATE.plusDays(1)
                );
        verifyNoInteractions(benchmarkPerformanceCalculator);
    }

    @Test
    void rejectsUnexpectedReportSymbolBeforeLoadingBenchmark() {
        SwingV1BacktestExperimentRequest request = request("000660");
        SwingV1BacktestRunRequest expected = runRequest(request, "000660");
        SwingV1BacktestReport unexpected = report(runRequest(request, "005930"));
        when(reportService.generate(expected)).thenReturn(unexpected);

        assertThatThrownBy(() -> service.execute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Generated report symbol must match requested candidate. "
                        + "expected=000660, actual=005930");
        verifyNoInteractions(benchmarkSeriesQueryService, benchmarkPerformanceCalculator);
    }

    @Test
    void preservesRequestedOrderInsteadOfSortingSymbols() {
        SwingV1BacktestExperimentRequest request = request("005930", "000660");
        SwingV1BacktestRunRequest first = runRequest(request, "005930");
        SwingV1BacktestRunRequest second = runRequest(request, "000660");
        SwingV1BacktestReport firstReport = report(first);
        SwingV1BacktestReport secondReport = report(second);
        when(reportService.generate(first)).thenReturn(firstReport);
        when(reportService.generate(second)).thenReturn(secondReport);

        SwingV1BacktestExperimentResult result = service.execute(request);

        assertThat(result.request().candidateSymbols()).containsExactly("005930", "000660");
        assertThat(result.reports()).containsExactly(firstReport, secondReport);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(reportService);
        order.verify(reportService).generate(first);
        order.verify(reportService).generate(second);
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
                MIDDLE_VALUATION_DATE,
                TO_VALUATION_DATE
        );
    }

    private SwingV1BacktestReport report(
            SwingV1BacktestRunRequest request,
            LocalDate... valuationDates
    ) {
        SwingV1BacktestReport report = mock(SwingV1BacktestReport.class);
        SwingV1BacktestRunResult runResult = mock(
                SwingV1BacktestRunResult.class
        );
        when(report.request()).thenReturn(request);
        when(report.runResult()).thenReturn(runResult);
        when(runResult.equityCurve()).thenReturn(
                List.of(valuationDates).stream()
                        .map(this::equitySnapshot)
                        .toList()
        );
        return report;
    }

    private BacktestBenchmarkSeries benchmarkSeries(
            LocalDate... observationDates
    ) {
        return new BacktestBenchmarkSeries(
                BENCHMARK_ID,
                List.of(observationDates).stream()
                        .map(date -> new BacktestBenchmarkObservation(
                                date,
                                new BigDecimal("100")
                        ))
                        .toList()
        );
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
                3
        );
    }
}
