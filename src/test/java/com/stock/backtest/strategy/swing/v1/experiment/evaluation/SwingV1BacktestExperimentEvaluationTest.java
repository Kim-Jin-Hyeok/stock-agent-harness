package com.stock.backtest.strategy.swing.v1.experiment.evaluation;

import com.stock.backtest.comparison.buyandhold.calculation.BuyAndHoldBacktestCalculator;
import com.stock.backtest.comparison.buyandhold.model.BuyAndHoldBacktestRequest;
import com.stock.backtest.comparison.buyandhold.model.BuyAndHoldBacktestResult;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.performance.metric.BacktestPerformanceCalculator;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunRequest;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunResult;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentRequest;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentResult;
import com.stock.backtest.strategy.swing.v1.experiment.summary.SwingV1BacktestExperimentSummary;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReport;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwingV1BacktestExperimentEvaluationTest {
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 9, 1);
    private final BuyAndHoldBacktestCalculator calculator = new BuyAndHoldBacktestCalculator(
            new TradeCostCalculator(), new BacktestPerformanceCalculator()
    );
    private final TradeCostModel costs = new TradeCostModel(
            "ZERO", 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO
    );
    private final List<Instant> instants = List.of(
            FIRST_DATE.atTime(9, 10).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
            FIRST_DATE.plusDays(1).atTime(9, 10).atZone(ZoneId.of("Asia/Seoul")).toInstant()
    );

    @Test
    void preservesMatchingExperimentAndCopiesCompleteComparisons() {
        Fixture fixture = fixture();
        List<BuyAndHoldBacktestResult> mutable = new ArrayList<>(comparisons());
        SwingV1BacktestExperimentEvaluation evaluation =
                new SwingV1BacktestExperimentEvaluation(fixture.result(), fixture.summary(), mutable);
        mutable.clear();

        assertThat(evaluation.experimentResult()).isSameAs(fixture.result());
        assertThat(evaluation.summary()).isSameAs(fixture.summary());
        assertThat(evaluation.buyAndHoldResults()).hasSize(2);
        assertThatThrownBy(() -> evaluation.buyAndHoldResults().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsSummaryFromDifferentExperiment() {
        Fixture fixture = fixture();
        SwingV1BacktestExperimentRequest other = mock(SwingV1BacktestExperimentRequest.class);
        when(fixture.summary().request()).thenReturn(other);

        assertThatThrownBy(() -> new SwingV1BacktestExperimentEvaluation(
                fixture.result(), fixture.summary(), comparisons()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Summary request must match experiment result.");
    }

    @Test
    void rejectsMissingDuplicateAndUnspecifiedAllocation() {
        Fixture fixture = fixture();
        List<BuyAndHoldBacktestResult> valid = comparisons();
        for (List<BuyAndHoldBacktestResult> invalid : List.of(
                List.<BuyAndHoldBacktestResult>of(),
                List.of(valid.getFirst()),
                List.of(valid.getFirst(), valid.getFirst()),
                List.of(valid.getFirst(), comparison(1_000L, new BigDecimal("0.2"), costs, instants))
        )) {
            assertThatThrownBy(() -> new SwingV1BacktestExperimentEvaluation(
                    fixture.result(), fixture.summary(), invalid
            )).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsComparisonCashCostModelAndValuationTimeMismatch() {
        Fixture fixture = fixture();
        TradeCostModel differentCosts = new TradeCostModel(
                "OTHER", 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO
        );
        for (BuyAndHoldBacktestResult invalid : List.of(
                comparison(900L, new BigDecimal("0.1"), costs, instants),
                comparison(1_000L, new BigDecimal("0.1"), differentCosts, instants),
                comparison(1_000L, new BigDecimal("0.1"), costs,
                        instants.stream().map(instant -> instant.plusSeconds(60)).toList())
        )) {
            assertThatThrownBy(() -> new SwingV1BacktestExperimentEvaluation(
                    fixture.result(), fixture.summary(), List.of(comparisons().getFirst(), invalid)
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Comparison cash, costModel and valuation instants "
                            + "must match the SWING report.");
        }
    }

    @Test
    void rejectsComparisonForDifferentSymbol() {
        Fixture fixture = fixture();
        BuyAndHoldBacktestRequest request = new BuyAndHoldBacktestRequest(
                "000660", 1_000L, new BigDecimal("0.1"), instants, costs
        );
        BuyAndHoldBacktestResult other = calculator.calculate(request, history("000660"));

        assertThatThrownBy(() -> new SwingV1BacktestExperimentEvaluation(
                fixture.result(), fixture.summary(), List.of(comparisons().getFirst(), other)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullComponents() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> new SwingV1BacktestExperimentEvaluation(
                null, fixture.summary(), comparisons()
        )).isInstanceOf(NullPointerException.class)
                .hasMessage("experimentResult must not be null.");
        assertThatThrownBy(() -> new SwingV1BacktestExperimentEvaluation(
                fixture.result(), null, comparisons()
        )).isInstanceOf(NullPointerException.class)
                .hasMessage("summary must not be null.");
        assertThatThrownBy(() -> new SwingV1BacktestExperimentEvaluation(
                fixture.result(), fixture.summary(), null
        )).isInstanceOf(NullPointerException.class)
                .hasMessage("buyAndHoldResults must not be null.");
    }

    private List<BuyAndHoldBacktestResult> comparisons() {
        return SwingV1BacktestExperimentEvaluation.BUY_AND_HOLD_INITIAL_ALLOCATION_RATIOS
                .stream().map(ratio -> comparison(1_000L, ratio, costs, instants)).toList();
    }

    private BuyAndHoldBacktestResult comparison(
            long cash, BigDecimal ratio, TradeCostModel model, List<Instant> valuationInstants
    ) {
        return calculator.calculate(
                new BuyAndHoldBacktestRequest("005930", cash, ratio, valuationInstants, model),
                history("005930")
        );
    }

    private DailyPriceHistory history(String symbol) {
        return new DailyPriceHistory(symbol, List.of(
                new DailyPriceBar(FIRST_DATE, 100L, 100L, 100L, 100L, 1L),
                new DailyPriceBar(FIRST_DATE.plusDays(1), 120L, 120L, 120L, 120L, 1L)
        ));
    }

    private Fixture fixture() {
        SwingV1BacktestExperimentRequest request = new SwingV1BacktestExperimentRequest(
                new InvestmentStrategyIdentity("SWING_V1", 1, InvestmentHorizon.SWING),
                "KOSPI", FIRST_DATE.minusDays(1), FIRST_DATE, 1_000L, costs
        );
        SwingV1BacktestReport report = mock(SwingV1BacktestReport.class);
        SwingV1BacktestRunResult run = mock(SwingV1BacktestRunResult.class);
        when(report.request()).thenReturn(new SwingV1BacktestRunRequest(
                request.strategyIdentity(), "005930", request.fromSignalDate(),
                request.toSignalDate(), BacktestPortfolioState.withCash(1_000L), costs
        ));
        when(report.runResult()).thenReturn(run);
        when(run.equityCurve()).thenReturn(instants.stream().map(instant ->
                new BacktestEquitySnapshot(
                        instant.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(),
                        instant, 1_000L, 0L, 1_000L
                )).toList());
        SwingV1BacktestExperimentResult result = mock(SwingV1BacktestExperimentResult.class);
        when(result.request()).thenReturn(request);
        when(result.reports()).thenReturn(List.of(report));
        SwingV1BacktestExperimentSummary summary = mock(SwingV1BacktestExperimentSummary.class);
        when(summary.request()).thenReturn(request);
        return new Fixture(result, summary);
    }

    private record Fixture(
            SwingV1BacktestExperimentResult result,
            SwingV1BacktestExperimentSummary summary
    ) {}
}
