package com.stock.backtest.strategy.swing.v1.experiment.evaluation;

import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentRequest;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentResult;
import com.stock.backtest.strategy.swing.v1.experiment.summary.SwingV1BacktestExperimentSummary;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwingV1BacktestExperimentEvaluationTest {
    @Test
    void preservesMatchingExperimentResultAndSummary() {
        SwingV1BacktestExperimentRequest request = mock(
                SwingV1BacktestExperimentRequest.class
        );
        SwingV1BacktestExperimentResult result = mock(
                SwingV1BacktestExperimentResult.class
        );
        SwingV1BacktestExperimentSummary summary = mock(
                SwingV1BacktestExperimentSummary.class
        );
        when(result.request()).thenReturn(request);
        when(summary.request()).thenReturn(request);

        SwingV1BacktestExperimentEvaluation evaluation =
                new SwingV1BacktestExperimentEvaluation(result, summary);

        assertThat(evaluation.experimentResult()).isSameAs(result);
        assertThat(evaluation.summary()).isSameAs(summary);
    }

    @Test
    void rejectsSummaryFromDifferentExperiment() {
        SwingV1BacktestExperimentResult result = mock(
                SwingV1BacktestExperimentResult.class
        );
        SwingV1BacktestExperimentSummary summary = mock(
                SwingV1BacktestExperimentSummary.class
        );
        when(result.request()).thenReturn(mock(
                SwingV1BacktestExperimentRequest.class
        ));
        when(summary.request()).thenReturn(mock(
                SwingV1BacktestExperimentRequest.class
        ));

        assertThatThrownBy(() -> new SwingV1BacktestExperimentEvaluation(
                result,
                summary
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Summary request must match experiment result."
                );
    }

    @Test
    void rejectsNullComponents() {
        SwingV1BacktestExperimentResult result = mock(
                SwingV1BacktestExperimentResult.class
        );
        SwingV1BacktestExperimentSummary summary = mock(
                SwingV1BacktestExperimentSummary.class
        );

        assertThatNullPointerException()
                .isThrownBy(() -> new SwingV1BacktestExperimentEvaluation(
                        null,
                        summary
                ))
                .withMessage("experimentResult must not be null.");
        assertThatNullPointerException()
                .isThrownBy(() -> new SwingV1BacktestExperimentEvaluation(
                        result,
                        null
                ))
                .withMessage("summary must not be null.");
    }
}
