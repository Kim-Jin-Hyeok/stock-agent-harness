package com.stock.backtest.strategy.swing.v1.experiment.evaluation;

import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentRequest;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentResult;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentService;
import com.stock.backtest.strategy.swing.v1.experiment.summary.SwingV1BacktestExperimentSummary;
import com.stock.backtest.strategy.swing.v1.experiment.summary.SwingV1BacktestExperimentSummaryCalculator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SwingV1BacktestExperimentEvaluationServiceTest {
    private final SwingV1BacktestExperimentService experimentService =
            mock(SwingV1BacktestExperimentService.class);
    private final SwingV1BacktestExperimentSummaryCalculator summaryCalculator =
            mock(SwingV1BacktestExperimentSummaryCalculator.class);
    private final SwingV1BacktestExperimentEvaluationService service =
            new SwingV1BacktestExperimentEvaluationService(
                    experimentService,
                    summaryCalculator
            );

    @Test
    void evaluatesExperimentOnceAndSummarizesItsResult() {
        SwingV1BacktestExperimentRequest request = mock(
                SwingV1BacktestExperimentRequest.class
        );
        SwingV1BacktestExperimentResult result = mock(
                SwingV1BacktestExperimentResult.class
        );
        SwingV1BacktestExperimentSummary summary = mock(
                SwingV1BacktestExperimentSummary.class
        );
        when(experimentService.execute(request)).thenReturn(result);
        when(summaryCalculator.calculate(result)).thenReturn(summary);
        when(result.request()).thenReturn(request);
        when(summary.request()).thenReturn(request);

        SwingV1BacktestExperimentEvaluation evaluation = service.evaluate(
                request
        );

        assertThat(evaluation.experimentResult()).isSameAs(result);
        assertThat(evaluation.summary()).isSameAs(summary);
        verify(experimentService).execute(request);
        verify(summaryCalculator).calculate(result);
    }

    @Test
    void skipsSummaryWhenExperimentFails() {
        SwingV1BacktestExperimentRequest request = mock(
                SwingV1BacktestExperimentRequest.class
        );
        when(experimentService.execute(request)).thenThrow(
                new IllegalStateException("Benchmark history is missing.")
        );

        assertThatThrownBy(() -> service.evaluate(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Benchmark history is missing.");
        verify(experimentService).execute(request);
        verifyNoInteractions(summaryCalculator);
    }

    @Test
    void propagatesSummaryFailureWithoutReturningPartialEvaluation() {
        SwingV1BacktestExperimentRequest request = mock(
                SwingV1BacktestExperimentRequest.class
        );
        SwingV1BacktestExperimentResult result = mock(
                SwingV1BacktestExperimentResult.class
        );
        when(experimentService.execute(request)).thenReturn(result);
        when(summaryCalculator.calculate(result)).thenThrow(
                new IllegalStateException("Summary calculation failed.")
        );

        assertThatThrownBy(() -> service.evaluate(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Summary calculation failed.");
        verify(experimentService).execute(request);
        verify(summaryCalculator).calculate(result);
    }

    @Test
    void rejectsNullRequestBeforeCallingCollaborators() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.evaluate(null))
                .withMessage("request must not be null.");
        verifyNoInteractions(experimentService, summaryCalculator);
    }
}
