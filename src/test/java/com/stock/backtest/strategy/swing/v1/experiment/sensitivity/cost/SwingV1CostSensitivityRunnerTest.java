package com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost;

import com.stock.backtest.strategy.swing.v1.experiment.evaluation.SwingV1BacktestExperimentEvaluationService;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentRequest;
import com.stock.backtest.strategy.swing.v1.experiment.runner.SwingV1BacktestManualRunner;
import com.stock.backtest.strategy.swing.v1.experiment.runner.config.SwingV1BacktestManualRunProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost.SwingV1CostSensitivityFixtures.model;
import static com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost.SwingV1CostSensitivityFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(OutputCaptureExtension.class)
class SwingV1CostSensitivityRunnerTest {
    @Test
    void logsFullResultsForAllThreeScenariosWithoutAnExtraBaselineRun(CapturedOutput output) {
        SwingV1CostSensitivityFixtures fixtures = new SwingV1CostSensitivityFixtures();
        SwingV1BacktestExperimentEvaluationService evaluationService = spy(fixtures.evaluationService);
        SwingV1CostSensitivityService sensitivityService = spy(new SwingV1CostSensitivityService(evaluationService));
        SwingV1BacktestManualRunner runner = new SwingV1BacktestManualRunner(
                evaluationService, sensitivityService, properties(request()));

        runner.run(new DefaultApplicationArguments());

        verify(sensitivityService).evaluate(any());
        verify(evaluationService, times(3)).evaluate(any());
        String log = output.getOut();
        assertThat(log).contains("SWING_V1 slippage sensitivity completed. scenarioCount=3",
                "swingMaxDrawdownRate=", "swingCompletedTradeCount=1", "swingFinalPositionQuantity=0",
                "swingExposureSummary=", "buyAndHoldExposureSummary=", "initialAllocationRatio=1,",
                "initialAllocationRatio=0.1,", "executedBuyCount=1, executedSellCount=1",
                "costModel=TradeCostModel[modelId=BASE,", "costModel=TradeCostModel[modelId=BASE_SLIPPAGE_X2,",
                "costModel=TradeCostModel[modelId=BASE_SLIPPAGE_X3,");
        assertThat(log.lines().filter(line -> line.contains("manual backtest completed.")).count()).isEqualTo(3);
        assertThat(log.lines().filter(line -> line.contains("manual backtest buy-and-hold comparison.")).count()).isEqualTo(12);
        assertThat(log.lines().filter(line -> line.contains("manual backtest diagnostic.")).count()).isEqualTo(6);
    }

    @Test
    void doesNotLogSuccessfulOrPartialResultsWhenAStressScenarioFails(CapturedOutput output) {
        SwingV1CostSensitivityFixtures fixtures = new SwingV1CostSensitivityFixtures();
        SwingV1BacktestExperimentEvaluationService evaluationService = spy(fixtures.evaluationService);
        SwingV1CostSensitivityRequest sensitivity = SwingV1CostSensitivityRequest.forSlippageStress(request());
        doThrow(new IllegalStateException("Stress evaluation failed.")).when(evaluationService)
                .evaluate(sensitivity.requestFor(sensitivity.costModels().get(1)));
        SwingV1BacktestManualRunner runner = new SwingV1BacktestManualRunner(evaluationService,
                new SwingV1CostSensitivityService(evaluationService), properties(request()));

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .hasMessage("Stress evaluation failed.");
        verify(evaluationService, times(2)).evaluate(any());
        assertThat(output.getOut()).contains("slippage sensitivity started.").doesNotContain(
                "slippage sensitivity completed.", "manual backtest completed.",
                "manual backtest buy-and-hold comparison.", "manual backtest diagnostic.");
    }

    @Test
    void rejectsInvalidStressBeforeAnyEvaluation(CapturedOutput output) {
        SwingV1BacktestExperimentRequest base = request();
        SwingV1BacktestExperimentRequest zero = new SwingV1BacktestExperimentRequest(
                base.strategyIdentity(), base.candidateSymbols(), base.benchmarkId(), base.fromSignalDate(),
                base.toSignalDate(), base.initialCashAmountKrwPerSymbol(), model("ZERO", "0", "0"));
        SwingV1BacktestExperimentEvaluationService evaluationService = mock(SwingV1BacktestExperimentEvaluationService.class);
        SwingV1CostSensitivityService sensitivityService = mock(SwingV1CostSensitivityService.class);
        SwingV1BacktestManualRunner runner = new SwingV1BacktestManualRunner(
                evaluationService, sensitivityService, properties(zero));

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .hasMessageContaining("at least one positive baseline slippage rate");
        verifyNoInteractions(evaluationService, sensitivityService);
        assertThat(output.getOut()).doesNotContain("slippage sensitivity completed.", "manual backtest completed.");
    }

    private SwingV1BacktestManualRunProperties properties(SwingV1BacktestExperimentRequest request) {
        return new SwingV1BacktestManualRunProperties(true, request.candidateSymbols(), request.fromSignalDate(),
                request.toSignalDate(), request.benchmarkId(), request.initialCashAmountKrwPerSymbol(),
                request.costModel(), true);
    }
}
