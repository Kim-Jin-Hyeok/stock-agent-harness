package com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost;

import com.stock.agent.evidence.swing.v1.SwingV1DecisionEvidence;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepResult;
import com.stock.backtest.strategy.swing.v1.experiment.evaluation.SwingV1BacktestExperimentEvaluation;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentResult;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReport;

import java.util.List;
import java.util.Objects;

public record SwingV1CostSensitivityResult(
        SwingV1CostSensitivityRequest request,
        List<SwingV1BacktestExperimentEvaluation> evaluations
) {
    public SwingV1CostSensitivityResult {
        Objects.requireNonNull(request, "request must not be null.");
        evaluations = List.copyOf(Objects.requireNonNull(
                evaluations, "evaluations must not be null."
        ));
        if (evaluations.size() != request.costModels().size()) {
            throw new IllegalArgumentException(
                    "Each requested costModel must have exactly one ordered evaluation."
            );
        }
        SwingV1BacktestExperimentResult baseline = evaluations.getFirst().experimentResult();
        for (int index = 0; index < evaluations.size(); index++) {
            SwingV1BacktestExperimentResult experiment = evaluations.get(index).experimentResult();
            if (!request.requestFor(request.costModels().get(index)).equals(experiment.request())) {
                throw new IllegalArgumentException(
                        "Evaluation request must match the ordered cost scenario exactly."
                );
            }
            validateSameMarketInputs(baseline, experiment);
        }
    }

    private static void validateSameMarketInputs(
            SwingV1BacktestExperimentResult baseline,
            SwingV1BacktestExperimentResult experiment
    ) {
        if (!baseline.benchmarkPerformanceSummary().equals(experiment.benchmarkPerformanceSummary())) {
            throw new IllegalArgumentException(
                    "Benchmark performance must remain fixed across cost scenarios."
            );
        }
        for (int index = 0; index < baseline.reports().size(); index++) {
            SwingV1BacktestReport baseReport = baseline.reports().get(index);
            SwingV1BacktestReport report = experiment.reports().get(index);
            List<BacktestEquitySnapshot> baseCurve = baseReport.runResult().equityCurve();
            List<BacktestEquitySnapshot> curve = report.runResult().equityCurve();
            if (!baseCurve.stream().map(BacktestEquitySnapshot::evaluatedAt).toList()
                    .equals(curve.stream().map(BacktestEquitySnapshot::evaluatedAt).toList())) {
                throw new IllegalArgumentException(
                        "Valuation instants must remain fixed across cost scenarios."
                );
            }
            List<SwingV1BacktestStepResult> baseSteps = baseReport.runResult().steps();
            List<SwingV1BacktestStepResult> steps = report.runResult().steps();
            if (baseSteps.size() != steps.size()) {
                throw new IllegalArgumentException(
                        "Signal steps must remain fixed across cost scenarios."
                );
            }
            for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
                validateSameStepInputs(baseSteps.get(stepIndex), steps.get(stepIndex));
            }
        }
    }

    private static void validateSameStepInputs(
            SwingV1BacktestStepResult baseline,
            SwingV1BacktestStepResult step
    ) {
        if (!baseline.signalDate().equals(step.signalDate())
                || !Objects.equals(baseline.decisionDate(), step.decisionDate())) {
            throw new IllegalArgumentException(
                    "Signal and decision dates must remain fixed across cost scenarios."
            );
        }
        if (baseline.decisionDate() == null) {
            return;
        }
        SwingV1DecisionEvidence baseEvidence = Objects.requireNonNull(
                baseline.decision().swingV1Evidence(),
                "Cost sensitivity requires SWING decision evidence."
        );
        SwingV1DecisionEvidence evidence = Objects.requireNonNull(
                step.decision().swingV1Evidence(),
                "Cost sensitivity requires SWING decision evidence."
        );
        if (!baseEvidence.currentPrice().equals(evidence.currentPrice())
                || baseEvidence.currentPriceSource() != evidence.currentPriceSource()
                || !baseEvidence.analysis().equals(evidence.analysis())) {
            throw new IllegalArgumentException(
                    "Decision market inputs must remain fixed across cost scenarios."
            );
        }
    }
}
