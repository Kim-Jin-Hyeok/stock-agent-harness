package com.stock.backtest.strategy.swing.v1.experiment.runner;

import com.stock.agent.InvestmentDecision;
import com.stock.backtest.performance.benchmark.BacktestBenchmarkPerformanceSummary;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.strategy.swing.v1.experiment.evaluation.SwingV1BacktestExperimentEvaluation;
import com.stock.backtest.strategy.swing.v1.experiment.evaluation.SwingV1BacktestExperimentEvaluationService;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentRequest;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentResult;
import com.stock.backtest.strategy.swing.v1.experiment.runner.config.SwingV1BacktestManualRunProperties;
import com.stock.backtest.strategy.swing.v1.experiment.summary.SwingV1BacktestExperimentSummary;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepResult;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepStatus;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunResult;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReport;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class SwingV1BacktestManualRunnerTest {
    private final SwingV1BacktestExperimentEvaluationService evaluationService =
            mock(SwingV1BacktestExperimentEvaluationService.class);

    @Test
    void runsConfiguredExperimentOnce(CapturedOutput output) throws Exception {
        SwingV1BacktestManualRunProperties properties = properties();
        SwingV1BacktestExperimentEvaluation evaluation = mock(
                SwingV1BacktestExperimentEvaluation.class
        );
        SwingV1BacktestExperimentSummary summary = mock(
                SwingV1BacktestExperimentSummary.class
        );
        SwingV1BacktestExperimentResult result = mock(
                SwingV1BacktestExperimentResult.class
        );
        BacktestBenchmarkPerformanceSummary benchmark = mock(
                BacktestBenchmarkPerformanceSummary.class
        );
        SwingV1BacktestReport report = mock(SwingV1BacktestReport.class);
        SwingV1BacktestRunResult runResult = mock(
                SwingV1BacktestRunResult.class
        );
        when(evaluationService.evaluate(any())).thenReturn(evaluation);
        when(evaluation.summary()).thenReturn(summary);
        when(evaluation.experimentResult()).thenReturn(result);
        when(result.benchmarkPerformanceSummary()).thenReturn(benchmark);
        when(result.reports()).thenReturn(List.of(report));
        when(report.runResult()).thenReturn(runResult);
        when(runResult.candidateSymbol()).thenReturn("005930");
        when(runResult.steps()).thenReturn(List.of());
        when(runResult.finalPortfolioState()).thenReturn(
                BacktestPortfolioState.withCash(10_000_000L)
        );
        SwingV1BacktestManualRunner runner = new SwingV1BacktestManualRunner(
                evaluationService,
                properties
        );

        runner.run(new DefaultApplicationArguments());

        ArgumentCaptor<SwingV1BacktestExperimentRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        SwingV1BacktestExperimentRequest.class
                );
        verify(evaluationService).evaluate(requestCaptor.capture());
        SwingV1BacktestExperimentRequest request = requestCaptor.getValue();
        assertThat(request.strategyIdentity()).isEqualTo(
                new InvestmentStrategyIdentity(
                        "SWING_V1",
                        1,
                        InvestmentHorizon.SWING
                )
        );
        assertThat(request.benchmarkId()).isEqualTo("KOSPI");
        assertThat(request.fromSignalDate())
                .isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(request.toSignalDate())
                .isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(request.initialCashAmountKrwPerSymbol())
                .isEqualTo(10_000_000L);
        assertThat(request.costModel()).isSameAs(properties.costModel());
        assertThat(output.getOut()).contains(
                "SWING_V1 manual backtest diagnostic. symbol=005930"
        ).contains("finalPositionQuantity=0");
    }

    @Test
    void propagatesEvaluationFailure() {
        when(evaluationService.evaluate(any())).thenThrow(
                new IllegalStateException("Stored benchmark history is missing.")
        );
        SwingV1BacktestManualRunner runner = new SwingV1BacktestManualRunner(
                evaluationService,
                properties()
        );

        assertThatThrownBy(() -> runner.run(
                new DefaultApplicationArguments()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Stored benchmark history is missing.");
        verify(evaluationService).evaluate(any());
    }

    @Test
    void doesNotLogCompletionWhenDiagnosticEvidenceIsMissing(
            CapturedOutput output
    ) {
        SwingV1BacktestExperimentEvaluation evaluation = mock(
                SwingV1BacktestExperimentEvaluation.class
        );
        SwingV1BacktestExperimentResult result = mock(
                SwingV1BacktestExperimentResult.class
        );
        SwingV1BacktestReport report = mock(SwingV1BacktestReport.class);
        SwingV1BacktestRunResult runResult = mock(
                SwingV1BacktestRunResult.class
        );
        SwingV1BacktestStepResult step = mock(
                SwingV1BacktestStepResult.class
        );
        when(evaluationService.evaluate(any())).thenReturn(evaluation);
        when(evaluation.experimentResult()).thenReturn(result);
        when(result.benchmarkPerformanceSummary()).thenReturn(mock(
                BacktestBenchmarkPerformanceSummary.class
        ));
        when(result.reports()).thenReturn(List.of(report));
        when(report.runResult()).thenReturn(runResult);
        when(runResult.steps()).thenReturn(List.of(step));
        when(step.status()).thenReturn(SwingV1BacktestStepStatus.HOLD);
        when(step.decision()).thenReturn(mock(InvestmentDecision.class));
        SwingV1BacktestManualRunner runner = new SwingV1BacktestManualRunner(
                evaluationService,
                properties()
        );

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("SWING_V1 step decision evidence must not be null.");
        assertThat(output.getOut()).doesNotContain(
                "SWING_V1 manual backtest completed."
        );
    }

    @Test
    void rejectsNullDependencies() {
        assertThatThrownBy(() -> new SwingV1BacktestManualRunner(
                null,
                properties()
        )).isInstanceOf(NullPointerException.class)
                .hasMessage("evaluationService must not be null.");
        assertThatThrownBy(() -> new SwingV1BacktestManualRunner(
                evaluationService,
                null
        )).isInstanceOf(NullPointerException.class)
                .hasMessage("properties must not be null.");
        verifyNoInteractions(evaluationService);
    }

    private SwingV1BacktestManualRunProperties properties() {
        return new SwingV1BacktestManualRunProperties(
                true,
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 28),
                "KOSPI",
                10_000_000L,
                new TradeCostModel(
                        "TEST_COST_V1",
                        1,
                        new BigDecimal("0.00015"),
                        new BigDecimal("0.00015"),
                        new BigDecimal("0.0018"),
                        new BigDecimal("0.001"),
                        new BigDecimal("0.001")
                )
        );
    }
}
