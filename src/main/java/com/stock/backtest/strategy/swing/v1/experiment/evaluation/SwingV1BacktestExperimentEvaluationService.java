package com.stock.backtest.strategy.swing.v1.experiment.evaluation;

import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentRequest;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentResult;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentService;
import com.stock.backtest.strategy.swing.v1.experiment.summary.SwingV1BacktestExperimentSummary;
import com.stock.backtest.strategy.swing.v1.experiment.summary.SwingV1BacktestExperimentSummaryCalculator;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class SwingV1BacktestExperimentEvaluationService {
    private final SwingV1BacktestExperimentService experimentService;
    private final SwingV1BacktestExperimentSummaryCalculator summaryCalculator;

    public SwingV1BacktestExperimentEvaluationService(
            SwingV1BacktestExperimentService experimentService,
            SwingV1BacktestExperimentSummaryCalculator summaryCalculator
    ) {
        this.experimentService = Objects.requireNonNull(
                experimentService,
                "experimentService must not be null."
        );
        this.summaryCalculator = Objects.requireNonNull(
                summaryCalculator,
                "summaryCalculator must not be null."
        );
    }

    public SwingV1BacktestExperimentEvaluation evaluate(
            SwingV1BacktestExperimentRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        SwingV1BacktestExperimentResult result = experimentService.execute(
                request
        );
        SwingV1BacktestExperimentSummary summary = summaryCalculator.calculate(
                result
        );
        return new SwingV1BacktestExperimentEvaluation(result, summary);
    }
}
