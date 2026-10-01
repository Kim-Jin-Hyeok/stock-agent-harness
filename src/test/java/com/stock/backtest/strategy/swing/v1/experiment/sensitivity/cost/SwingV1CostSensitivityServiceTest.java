package com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost;

import com.stock.agent.InvestmentAction;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.strategy.swing.v1.experiment.evaluation.SwingV1BacktestExperimentEvaluation;
import com.stock.backtest.strategy.swing.v1.experiment.evaluation.SwingV1BacktestExperimentEvaluationService;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentRequest;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost.SwingV1CostSensitivityFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class SwingV1CostSensitivityServiceTest {
    @Test
    void rerunsEachScenarioOnceInOrderFromIndependentInitialCash() {
        SwingV1CostSensitivityFixtures fixtures = new SwingV1CostSensitivityFixtures();
        SwingV1BacktestExperimentEvaluationService evaluationService = spy(fixtures.evaluationService);
        SwingV1CostSensitivityRequest sensitivity = SwingV1CostSensitivityRequest.forSlippageStress(request());

        SwingV1CostSensitivityResult result = new SwingV1CostSensitivityService(evaluationService).evaluate(sensitivity);

        ArgumentCaptor<SwingV1BacktestExperimentRequest> captor =
                ArgumentCaptor.forClass(SwingV1BacktestExperimentRequest.class);
        verify(evaluationService, times(3)).evaluate(captor.capture());
        assertThat(captor.getAllValues()).containsExactlyElementsOf(sensitivity.costModels().stream()
                .map(sensitivity::requestFor).toList());
        assertThat(result.request()).isSameAs(sensitivity);
        assertThat(result.evaluations()).hasSize(3);
        assertThat(result.evaluations()).allSatisfy(evaluation -> {
            assertThat(evaluation.experimentResult().reports()).extracting(report -> report.request().candidateSymbol())
                    .containsExactly("000660", "005930");
            assertThat(evaluation.experimentResult().reports()).allSatisfy(report -> {
                assertThat(report.request().initialPortfolioState()).isEqualTo(BacktestPortfolioState.withCash(1_000_000L));
                assertThat(report.runResult().initialPortfolioState()).isEqualTo(BacktestPortfolioState.withCash(1_000_000L));
                assertThat(report.completedTrades()).hasSize(1);
            });
        });
        verifyNoMoreInteractions(evaluationService);
    }

    @Test
    void appliesEachModelToSwingFillsAndBothBuyAndHoldAllocations() {
        SwingV1CostSensitivityFixtures fixtures = new SwingV1CostSensitivityFixtures();
        SwingV1CostSensitivityResult result = new SwingV1CostSensitivityService(fixtures.evaluationService)
                .evaluate(SwingV1CostSensitivityRequest.forSlippageStress(request()));

        for (SwingV1BacktestExperimentEvaluation evaluation : result.evaluations()) {
            TradeCostModel costModel = evaluation.experimentResult().request().costModel();
            assertThat(evaluation.experimentResult().reports()).allSatisfy(report -> {
                assertThat(report.request().costModel()).isEqualTo(costModel);
                assertThat(report.completedTrades()).allSatisfy(trade -> {
                    assertThat(trade.entryFill().tradeCostCalculation().costModel()).isEqualTo(costModel);
                    assertThat(trade.exitFill().tradeCostCalculation().costModel()).isEqualTo(costModel);
                });
                assertThat(report.runResult().steps().getFirst().decision().action()).isEqualTo(InvestmentAction.BUY);
                assertThat(report.exposureSummary().investedObservationCount()).isPositive();
            });
            assertThat(evaluation.buyAndHoldResults()).hasSize(4).allSatisfy(comparison -> {
                assertThat(comparison.request().costModel()).isEqualTo(costModel);
                assertThat(comparison.boughtQuantity()).isPositive();
            });
        }
        assertThat(result.evaluations()).extracting(evaluation -> evaluation.experimentResult().reports().getFirst()
                .runResult().finalPortfolioState().cashAmountKrw()).doesNotHaveDuplicates();
        assertThat(result.evaluations()).extracting(evaluation -> evaluation.buyAndHoldResults().getFirst()
                .performanceSummary().finalEquityAmountKrw()).doesNotHaveDuplicates();
    }

    @Test
    void reproducesDirectBaselineAndRepeatedEvaluationOnFixedInputs() {
        SwingV1CostSensitivityFixtures fixtures = new SwingV1CostSensitivityFixtures();
        SwingV1BacktestExperimentEvaluation direct = fixtures.evaluate(request());
        SwingV1CostSensitivityService service = new SwingV1CostSensitivityService(fixtures.evaluationService);
        SwingV1CostSensitivityRequest sensitivity = SwingV1CostSensitivityRequest.forSlippageStress(request());

        SwingV1CostSensitivityResult first = service.evaluate(sensitivity);
        SwingV1CostSensitivityResult second = service.evaluate(sensitivity);

        assertThat(first.evaluations().getFirst()).isEqualTo(direct);
        assertThat(second).isEqualTo(first);
        assertThat(sensitivity.baseRequest()).isEqualTo(request());
    }

    @Test
    void stopsAtFailingScenarioWithoutSkippingItOrReturningPartialSuccess() {
        SwingV1CostSensitivityFixtures fixtures = new SwingV1CostSensitivityFixtures();
        SwingV1BacktestExperimentEvaluationService evaluationService = spy(fixtures.evaluationService);
        SwingV1CostSensitivityRequest sensitivity = SwingV1CostSensitivityRequest.forSlippageStress(request());
        SwingV1BacktestExperimentRequest stress = sensitivity.requestFor(sensitivity.costModels().get(1));
        IllegalStateException failure = new IllegalStateException("Stored prices unavailable.");
        doThrow(failure).when(evaluationService).evaluate(stress);

        assertThatThrownBy(() -> new SwingV1CostSensitivityService(evaluationService).evaluate(sensitivity))
                .isSameAs(failure);
        InOrder order = inOrder(evaluationService);
        order.verify(evaluationService).evaluate(sensitivity.baseRequest());
        order.verify(evaluationService).evaluate(stress);
        verifyNoMoreInteractions(evaluationService);
    }

    @Test
    void retainsNoTradeAndLosingResultsWithoutFilteringSymbols() {
        SwingV1CostSensitivityFixtures fixtures = new SwingV1CostSensitivityFixtures();
        SwingV1BacktestExperimentRequest base = request();
        SwingV1BacktestExperimentRequest noEntry = new SwingV1BacktestExperimentRequest(
                base.strategyIdentity(), base.candidateSymbols(), base.benchmarkId(),
                base.fromSignalDate().plusDays(1), base.toSignalDate(),
                base.initialCashAmountKrwPerSymbol(), base.costModel());
        SwingV1CostSensitivityService service = new SwingV1CostSensitivityService(fixtures.evaluationService);

        SwingV1CostSensitivityResult noTrade = service.evaluate(SwingV1CostSensitivityRequest.forSlippageStress(noEntry));
        assertThat(noTrade.evaluations()).allSatisfy(evaluation -> {
            assertThat(evaluation.summary().symbolCount()).isEqualTo(2);
            assertThat(evaluation.summary().totalCompletedTradeCount()).isZero();
            assertThat(evaluation.summary().medianLiquidationAdjustedReturnRate()).isEqualByComparingTo("0");
            assertThat(evaluation.experimentResult().reports()).allSatisfy(report ->
                    assertThat(report.exposureSummary().investedObservationCount()).isZero());
        });
        SwingV1CostSensitivityResult losing = service.evaluate(SwingV1CostSensitivityRequest.forSlippageStress(base));
        assertThat(losing.evaluations()).allSatisfy(evaluation -> {
            assertThat(evaluation.summary().symbolCount()).isEqualTo(2);
            assertThat(evaluation.summary().losingSymbolCount()).isEqualTo(2);
        });
    }

    @Test
    void rejectsNullDependenciesAndRequestBeforeCallingEvaluation() {
        SwingV1BacktestExperimentEvaluationService evaluationService = mock(SwingV1BacktestExperimentEvaluationService.class);
        assertThatNullPointerException().isThrownBy(() -> new SwingV1CostSensitivityService(null))
                .withMessage("evaluationService must not be null.");
        assertThatNullPointerException().isThrownBy(() -> new SwingV1CostSensitivityService(evaluationService).evaluate(null))
                .withMessage("request must not be null.");
        verifyNoInteractions(evaluationService);
    }
}
