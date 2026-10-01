package com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost;

import com.stock.backtest.strategy.swing.v1.experiment.evaluation.SwingV1BacktestExperimentEvaluation;
import com.stock.backtest.strategy.swing.v1.experiment.evaluation.SwingV1BacktestExperimentEvaluationService;
import com.stock.trade.cost.model.TradeCostModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class SwingV1CostSensitivityService {
    private final SwingV1BacktestExperimentEvaluationService evaluationService;

    public SwingV1CostSensitivityService(SwingV1BacktestExperimentEvaluationService evaluationService) {
        this.evaluationService = Objects.requireNonNull(
                evaluationService, "evaluationService must not be null."
        );
    }

    public SwingV1CostSensitivityResult evaluate(SwingV1CostSensitivityRequest request) {
        Objects.requireNonNull(request, "request must not be null.");
        List<SwingV1BacktestExperimentEvaluation> evaluations = new ArrayList<>();
        for (TradeCostModel costModel : request.costModels()) {
            // Rerun from initial cash; costs can change all subsequent portfolio decisions.
            evaluations.add(evaluationService.evaluate(request.requestFor(costModel)));
        }
        return new SwingV1CostSensitivityResult(request, evaluations);
    }
}
