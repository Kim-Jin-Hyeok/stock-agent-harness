package com.stock.backtest.strategy.swing.v1.experiment.evaluation;

import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentResult;
import com.stock.backtest.strategy.swing.v1.experiment.summary.SwingV1BacktestExperimentSummary;

import java.util.Objects;

public record SwingV1BacktestExperimentEvaluation(
        SwingV1BacktestExperimentResult experimentResult,
        SwingV1BacktestExperimentSummary summary
) {
    public SwingV1BacktestExperimentEvaluation {
        Objects.requireNonNull(
                experimentResult,
                "experimentResult must not be null."
        );
        Objects.requireNonNull(summary, "summary must not be null.");
        if (!experimentResult.request().equals(summary.request())) {
            throw new IllegalArgumentException(
                    "Summary request must match experiment result."
            );
        }
    }
}
