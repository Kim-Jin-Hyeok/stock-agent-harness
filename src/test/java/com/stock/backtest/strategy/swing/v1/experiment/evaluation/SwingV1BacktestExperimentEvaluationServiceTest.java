package com.stock.backtest.strategy.swing.v1.experiment.evaluation;

import com.stock.agent.InvestmentDecision;
import com.stock.agent.evidence.swing.v1.SwingV1DecisionEvidence;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepResult;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.backtest.comparison.buyandhold.calculation.BuyAndHoldBacktestCalculator;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.performance.metric.BacktestPerformanceCalculator;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunRequest;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunResult;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentRequest;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentResult;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentService;
import com.stock.backtest.strategy.swing.v1.experiment.summary.SwingV1BacktestExperimentSummary;
import com.stock.backtest.strategy.swing.v1.experiment.summary.SwingV1BacktestExperimentSummaryCalculator;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReport;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SwingV1BacktestExperimentEvaluationServiceTest {
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 9, 1);
    private final SwingV1BacktestExperimentService experimentService =
            mock(SwingV1BacktestExperimentService.class);
    private final SwingV1BacktestExperimentSummaryCalculator summaryCalculator =
            mock(SwingV1BacktestExperimentSummaryCalculator.class);
    private final DailyPriceHistoryQueryService priceHistoryQueryService =
            mock(DailyPriceHistoryQueryService.class);
    private final BuyAndHoldBacktestCalculator comparisonCalculator = spy(
            new BuyAndHoldBacktestCalculator(
                    new TradeCostCalculator(), new BacktestPerformanceCalculator()
            )
    );
    private final SwingV1BacktestExperimentEvaluationService service =
            new SwingV1BacktestExperimentEvaluationService(
                    experimentService, summaryCalculator,
                    priceHistoryQueryService, comparisonCalculator
            );

    @Test
    void evaluatesExperimentOnceAndReusesOneHistoryQueryForBothAllocations() {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestExperimentResult result = result(request, List.of("005930"));
        SwingV1BacktestExperimentSummary summary = summary(request, result);
        when(experimentService.execute(request)).thenReturn(result);
        when(priceHistoryQueryService.getDailyPriceHistory(any()))
                .thenReturn(history("005930"));

        SwingV1BacktestExperimentEvaluation evaluation = service.evaluate(request);

        assertThat(evaluation.experimentResult()).isSameAs(result);
        assertThat(evaluation.summary()).isSameAs(summary);
        assertThat(evaluation.buyAndHoldResults()).hasSize(2);
        assertThat(evaluation.buyAndHoldResults())
                .extracting(comparison -> comparison.request().initialAllocationRatio())
                .containsExactly(BigDecimal.ONE, new BigDecimal("0.1"));
        assertThat(evaluation.buyAndHoldResults())
                .extracting(comparison -> comparison.boughtQuantity())
                .containsExactly(10L, 1L);
        verify(experimentService).execute(request);
        verify(summaryCalculator).calculate(result);
        verify(priceHistoryQueryService).getDailyPriceHistory(
                new DailyPriceHistoryRequest("005930", FIRST_DATE, FIRST_DATE.plusDays(2))
        );
        verify(comparisonCalculator, times(2)).calculate(any(), any());
    }

    @Test
    void comparesEachSymbolWithItsOwnPricesAndIndependentCash() {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestExperimentResult result = result(request, List.of("005930", "000660"));
        summary(request, result);
        when(experimentService.execute(request)).thenReturn(result);
        when(priceHistoryQueryService.getDailyPriceHistory(any())).thenAnswer(invocation -> {
            DailyPriceHistoryRequest query = invocation.getArgument(0);
            return history(query.symbol());
        });

        SwingV1BacktestExperimentEvaluation evaluation = service.evaluate(request);

        assertThat(evaluation.buyAndHoldResults()).hasSize(4);
        assertThat(evaluation.buyAndHoldResults())
                .extracting(comparison -> comparison.request().symbol())
                .containsExactly("005930", "005930", "000660", "000660");
        assertThat(evaluation.buyAndHoldResults()).allSatisfy(comparison ->
                assertThat(comparison.request().initialCashAmountKrw()).isEqualTo(1_000L)
        );
        verify(priceHistoryQueryService, times(2)).getDailyPriceHistory(any());
    }

    @Test
    void skipsSummaryAndComparisonsWhenExperimentFails() {
        SwingV1BacktestExperimentRequest request = request();
        when(experimentService.execute(request)).thenThrow(
                new IllegalStateException("Benchmark history is missing.")
        );

        assertThatThrownBy(() -> service.evaluate(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Benchmark history is missing.");
        verifyNoInteractions(summaryCalculator, priceHistoryQueryService, comparisonCalculator);
    }

    @Test
    void propagatesSummaryFailureWithoutReturningPartialEvaluation() {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestExperimentResult result = result(request, List.of("005930"));
        when(experimentService.execute(request)).thenReturn(result);
        when(summaryCalculator.calculate(result)).thenThrow(
                new IllegalStateException("Summary calculation failed.")
        );

        assertThatThrownBy(() -> service.evaluate(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Summary calculation failed.");
        verifyNoInteractions(priceHistoryQueryService, comparisonCalculator);
    }

    @Test
    void propagatesComparisonDataFailureWithoutReturningPartialEvaluation() {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestExperimentResult result = result(request, List.of("005930"));
        summary(request, result);
        when(experimentService.execute(request)).thenReturn(result);
        when(priceHistoryQueryService.getDailyPriceHistory(any()))
                .thenReturn(new DailyPriceHistory("005930", List.of()));

        assertThatThrownBy(() -> service.evaluate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("History trading dates must exactly match valuation dates.");
    }

    @Test
    void rejectsEmptyReportEquityCurveBeforeHistoryQuery() {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestExperimentResult result = result(request, List.of("005930"));
        summary(request, result);
        when(experimentService.execute(request)).thenReturn(result);
        when(result.reports().getFirst().runResult().equityCurve()).thenReturn(List.of());

        assertThatThrownBy(() -> service.evaluate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Report equityCurve must not be empty.");
        verifyNoInteractions(priceHistoryQueryService, comparisonCalculator);
    }

    @Test
    void rejectsPricesChangedAfterTheSwingRun() {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestExperimentResult result = result(request, List.of("005930"));
        summary(request, result);
        when(experimentService.execute(request)).thenReturn(result);
        when(priceHistoryQueryService.getDailyPriceHistory(any())).thenReturn(
                new DailyPriceHistory("005930", List.of(
                        new DailyPriceBar(FIRST_DATE, 101L, 101L, 101L, 101L, 1L),
                        history("005930").bars().get(1), history("005930").bars().get(2)
                ))
        );

        assertThatThrownBy(() -> service.evaluate(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Comparison opening prices must match SWING decision evidence.");
        verifyNoInteractions(comparisonCalculator);
    }

    @Test
    void rejectsMissingDailyOpenDecisionEvidence() {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestExperimentResult result = result(request, List.of("005930"));
        summary(request, result);
        when(experimentService.execute(request)).thenReturn(result);
        when(priceHistoryQueryService.getDailyPriceHistory(any())).thenReturn(history("005930"));
        when(result.reports().getFirst().runResult().steps().getFirst().decision()
                .swingV1Evidence()).thenReturn(null);

        assertThatThrownBy(() -> service.evaluate(request))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("SWING comparison requires daily-open decision evidence.");
        verifyNoInteractions(comparisonCalculator);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOURCE", "SYMBOL", "OBSERVED_AT"})
    void rejectsInconsistentOpeningPriceMetadata(String field) {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestExperimentResult result = result(request, List.of("005930"));
        summary(request, result);
        when(experimentService.execute(request)).thenReturn(result);
        when(priceHistoryQueryService.getDailyPriceHistory(any())).thenReturn(history("005930"));
        SwingV1BacktestStepResult first = result.reports().getFirst().runResult().steps().getFirst();
        SwingV1DecisionEvidence evidence = first.decision().swingV1Evidence();
        if (field.equals("SOURCE")) {
            when(evidence.currentPriceSource()).thenReturn(CurrentPriceLookupSource.CACHE);
        } else {
            CurrentPriceSnapshot changedPrice = new CurrentPriceSnapshot(
                    field.equals("SYMBOL") ? "000660" : "005930",
                    100L,
                    field.equals("OBSERVED_AT")
                            ? first.equitySnapshot().evaluatedAt().plusSeconds(60)
                            : first.equitySnapshot().evaluatedAt()
            );
            when(evidence.currentPrice()).thenReturn(changedPrice);
        }

        assertThatThrownBy(() -> service.evaluate(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Comparison opening prices must match SWING decision evidence.");
        verifyNoInteractions(comparisonCalculator);
    }

    @Test
    void rejectsNullRequestBeforeCallingCollaborators() {
        assertThatNullPointerException().isThrownBy(() -> service.evaluate(null))
                .withMessage("request must not be null.");
        verifyNoInteractions(experimentService, summaryCalculator,
                priceHistoryQueryService, comparisonCalculator);
    }

    @Test
    void rejectsNullDependencies() {
        assertThatThrownBy(() -> new SwingV1BacktestExperimentEvaluationService(
                null, summaryCalculator, priceHistoryQueryService, comparisonCalculator
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SwingV1BacktestExperimentEvaluationService(
                experimentService, null, priceHistoryQueryService, comparisonCalculator
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SwingV1BacktestExperimentEvaluationService(
                experimentService, summaryCalculator, null, comparisonCalculator
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SwingV1BacktestExperimentEvaluationService(
                experimentService, summaryCalculator, priceHistoryQueryService, null
        )).isInstanceOf(NullPointerException.class);
    }

    private SwingV1BacktestExperimentRequest request() {
        return new SwingV1BacktestExperimentRequest(
                new InvestmentStrategyIdentity("SWING_V1", 1, InvestmentHorizon.SWING),
                "KOSPI", FIRST_DATE.minusDays(1), FIRST_DATE.plusDays(1), 1_000L,
                new TradeCostModel("ZERO", 1, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        );
    }

    private SwingV1BacktestExperimentResult result(
            SwingV1BacktestExperimentRequest request, List<String> symbols
    ) {
        SwingV1BacktestExperimentResult result = mock(SwingV1BacktestExperimentResult.class);
        when(result.request()).thenReturn(request);
        List<SwingV1BacktestReport> reports = symbols.stream().map(symbol -> {
            SwingV1BacktestReport report = mock(SwingV1BacktestReport.class);
            SwingV1BacktestRunResult run = mock(SwingV1BacktestRunResult.class);
            when(report.request()).thenReturn(new SwingV1BacktestRunRequest(
                    request.strategyIdentity(), symbol, request.fromSignalDate(),
                    request.toSignalDate(), BacktestPortfolioState.withCash(1_000L),
                    request.costModel()
            ));
            when(report.runResult()).thenReturn(run);
            List<BacktestEquitySnapshot> curve = IntStream.range(0, 3)
                    .mapToObj(index -> {
                        LocalDate date = FIRST_DATE.plusDays(index);
                        return new BacktestEquitySnapshot(
                                date, date.atTime(9, 10).atZone(ZoneId.of("Asia/Seoul"))
                                        .toInstant(), 1_000L, 0L, 1_000L
                        );
                    }).toList();
            when(run.equityCurve()).thenReturn(curve);
            List<SwingV1BacktestStepResult> steps = IntStream.range(0, 3)
                    .mapToObj(index -> {
                        SwingV1BacktestStepResult step = mock(SwingV1BacktestStepResult.class);
                        InvestmentDecision decision = mock(InvestmentDecision.class);
                        SwingV1DecisionEvidence evidence = mock(SwingV1DecisionEvidence.class);
                        when(step.equitySnapshot()).thenReturn(curve.get(index));
                        when(step.decision()).thenReturn(decision);
                        when(decision.swingV1Evidence()).thenReturn(evidence);
                        when(evidence.currentPriceSource())
                                .thenReturn(CurrentPriceLookupSource.BACKTEST_DAILY_OPEN);
                        when(evidence.currentPrice()).thenReturn(new CurrentPriceSnapshot(
                                symbol, 100L + index * 10L, curve.get(index).evaluatedAt()
                        ));
                        return step;
                    }).toList();
            when(run.steps()).thenReturn(steps);
            return report;
        }).toList();
        when(result.reports()).thenReturn(reports);
        return result;
    }

    private SwingV1BacktestExperimentSummary summary(
            SwingV1BacktestExperimentRequest request, SwingV1BacktestExperimentResult result
    ) {
        SwingV1BacktestExperimentSummary summary = mock(SwingV1BacktestExperimentSummary.class);
        when(summary.request()).thenReturn(request);
        when(summaryCalculator.calculate(result)).thenReturn(summary);
        return summary;
    }

    private DailyPriceHistory history(String symbol) {
        return new DailyPriceHistory(symbol, IntStream.range(0, 3)
                .mapToObj(index -> new DailyPriceBar(
                        FIRST_DATE.plusDays(index), 100L + index * 10L,
                        100L + index * 10L, 100L + index * 10L,
                        100L + index * 10L, 1_000L
                )).toList());
    }
}
