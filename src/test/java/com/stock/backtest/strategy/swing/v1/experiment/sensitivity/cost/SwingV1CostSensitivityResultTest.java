package com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost;

import com.stock.agent.InvestmentDecision;
import com.stock.agent.evidence.swing.v1.SwingV1DecisionEvidence;
import com.stock.backtest.performance.benchmark.BacktestBenchmarkPerformanceSummary;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepResult;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunResult;
import com.stock.backtest.strategy.swing.v1.experiment.evaluation.SwingV1BacktestExperimentEvaluation;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentRequest;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentResult;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReport;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost.SwingV1CostSensitivityFixtures.model;
import static com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost.SwingV1CostSensitivityFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwingV1CostSensitivityResultTest {
    private final SwingV1CostSensitivityRequest sensitivity = SwingV1CostSensitivityRequest.forSlippageStress(request());
    private final SwingV1CostSensitivityFixtures fixtures = new SwingV1CostSensitivityFixtures();

    @Test
    void preservesFullOrderedEvaluationsInAnImmutableList() {
        List<SwingV1BacktestExperimentEvaluation> evaluations = new ArrayList<>(evaluations());
        List<SwingV1BacktestExperimentEvaluation> expected = List.copyOf(evaluations);

        SwingV1CostSensitivityResult result = new SwingV1CostSensitivityResult(sensitivity, evaluations);
        evaluations.clear();

        assertThat(result.evaluations()).containsExactlyElementsOf(expected);
        assertThat(result.evaluations().getFirst()).isSameAs(expected.getFirst());
        assertThatThrownBy(() -> result.evaluations().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullRequestsListsAndEvaluations() {
        assertThatNullPointerException().isThrownBy(() -> new SwingV1CostSensitivityResult(null, List.of()));
        assertThatNullPointerException().isThrownBy(() -> new SwingV1CostSensitivityResult(sensitivity, null));
        assertThatNullPointerException().isThrownBy(() -> new SwingV1CostSensitivityResult(sensitivity,
                Arrays.asList(null, null, null)));
    }

    @Test
    void rejectsMissingExtraReorderedAndDuplicateScenarios() {
        List<SwingV1BacktestExperimentEvaluation> evaluations = evaluations();
        assertThatThrownBy(() -> new SwingV1CostSensitivityResult(sensitivity, evaluations.subList(0, 2)))
                .hasMessageContaining("exactly one ordered evaluation");
        List<SwingV1BacktestExperimentEvaluation> extra = new ArrayList<>(evaluations);
        extra.add(evaluations.getFirst());
        assertThatThrownBy(() -> new SwingV1CostSensitivityResult(sensitivity, extra))
                .hasMessageContaining("exactly one ordered evaluation");
        List<SwingV1BacktestExperimentEvaluation> reordered = new ArrayList<>(evaluations);
        Collections.swap(reordered, 0, 1);
        assertThatThrownBy(() -> new SwingV1CostSensitivityResult(sensitivity, reordered))
                .hasMessageContaining("ordered cost scenario exactly");
        assertThatThrownBy(() -> new SwingV1CostSensitivityResult(sensitivity,
                List.of(evaluations.getFirst(), evaluations.getFirst(), evaluations.getLast())))
                .hasMessageContaining("ordered cost scenario exactly");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANDIDATE_ORDER", "FROM_DATE", "TO_DATE", "CASH", "BENCHMARK", "COST"})
    void rejectsChangesToRequestedScenarioConditions(String field) {
        List<SwingV1BacktestExperimentEvaluation> evaluations = new ArrayList<>(evaluations());
        SwingV1BacktestExperimentRequest expected = sensitivity.requestFor(sensitivity.costModels().get(1));
        SwingV1BacktestExperimentRequest changed = new SwingV1BacktestExperimentRequest(
                expected.strategyIdentity(), field.equals("CANDIDATE_ORDER") ? List.of("005930", "000660") : expected.candidateSymbols(),
                field.equals("BENCHMARK") ? "KOSDAQ" : expected.benchmarkId(),
                field.equals("FROM_DATE") ? expected.fromSignalDate().minusDays(1) : expected.fromSignalDate(),
                field.equals("TO_DATE") ? expected.toSignalDate().plusDays(1) : expected.toSignalDate(),
                field.equals("CASH") ? 2_000_000L : expected.initialCashAmountKrwPerSymbol(),
                field.equals("COST") ? model("OTHER", "0.004", "0.004") : expected.costModel());
        SwingV1BacktestExperimentResult experiment = changedExperiment(evaluations.get(1));
        when(experiment.request()).thenReturn(changed);
        evaluations.set(1, evaluation(experiment));

        assertThatThrownBy(() -> new SwingV1CostSensitivityResult(sensitivity, evaluations))
                .hasMessageContaining("ordered cost scenario exactly");
    }

    @Test
    void rejectsDifferentBenchmarkPerformance() {
        List<SwingV1BacktestExperimentEvaluation> evaluations = new ArrayList<>(evaluations());
        SwingV1BacktestExperimentResult experiment = changedExperiment(evaluations.get(1));
        when(experiment.benchmarkPerformanceSummary()).thenReturn(mock(BacktestBenchmarkPerformanceSummary.class));
        evaluations.set(1, evaluation(experiment));

        assertThatThrownBy(() -> new SwingV1CostSensitivityResult(sensitivity, evaluations))
                .hasMessage("Benchmark performance must remain fixed across cost scenarios.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"VALUATION", "STEP_COUNT", "SIGNAL_DATE", "DECISION_DATE", "PRICE", "SOURCE", "ANALYSIS", "MISSING_EVIDENCE"})
    void rejectsChangedValuationOrDecisionMarketInputs(String field) {
        List<SwingV1BacktestExperimentEvaluation> evaluations = new ArrayList<>(evaluations());
        SwingV1BacktestExperimentResult experiment = changedExperiment(evaluations.get(1));
        SwingV1BacktestReport originalReport = experiment.reports().getFirst();
        SwingV1BacktestReport report = mock(SwingV1BacktestReport.class);
        SwingV1BacktestRunResult run = mock(SwingV1BacktestRunResult.class);
        when(report.runResult()).thenReturn(run);
        when(run.equityCurve()).thenReturn(originalReport.runResult().equityCurve());
        List<SwingV1BacktestStepResult> steps = new ArrayList<>(originalReport.runResult().steps());
        SwingV1BacktestStepResult originalStep = steps.getFirst();
        SwingV1BacktestStepResult step = mock(SwingV1BacktestStepResult.class);
        when(step.signalDate()).thenReturn(originalStep.signalDate());
        when(step.decisionDate()).thenReturn(originalStep.decisionDate());
        when(step.decision()).thenReturn(originalStep.decision());
        steps.set(0, step);
        if (field.equals("VALUATION")) {
            BacktestEquitySnapshot first = run.equityCurve().getFirst();
            when(run.equityCurve()).thenReturn(List.of(new BacktestEquitySnapshot(first.valuationDate(),
                    first.evaluatedAt().plusSeconds(1), first.cashAmountKrw(),
                    first.positionEvaluationAmountKrw(), first.totalAssetAmountKrw())));
        } else if (field.equals("STEP_COUNT")) {
            steps.removeLast();
        } else if (field.equals("SIGNAL_DATE")) {
            when(step.signalDate()).thenReturn(originalStep.signalDate().minusDays(1));
        } else if (field.equals("DECISION_DATE")) {
            when(step.decisionDate()).thenReturn(originalStep.decisionDate().plusDays(1));
        } else {
            InvestmentDecision decision = mock(InvestmentDecision.class);
            SwingV1DecisionEvidence originalEvidence = originalStep.decision().swingV1Evidence();
            SwingV1DecisionEvidence evidence = mock(SwingV1DecisionEvidence.class);
            when(step.decision()).thenReturn(decision);
            when(decision.swingV1Evidence()).thenReturn(field.equals("MISSING_EVIDENCE") ? null : evidence);
            when(evidence.currentPrice()).thenReturn(originalEvidence.currentPrice());
            when(evidence.currentPriceSource()).thenReturn(originalEvidence.currentPriceSource());
            when(evidence.analysis()).thenReturn(originalEvidence.analysis());
            if (field.equals("PRICE")) {
                CurrentPriceSnapshot price = originalEvidence.currentPrice();
                when(evidence.currentPrice()).thenReturn(new CurrentPriceSnapshot(
                        price.symbol(), price.priceKrw() + 1, price.observedAt()));
            } else if (field.equals("SOURCE")) {
                when(evidence.currentPriceSource()).thenReturn(CurrentPriceLookupSource.CACHE);
            } else if (field.equals("ANALYSIS")) {
                when(evidence.analysis()).thenReturn(mock(SwingTechnicalAnalysisResult.class));
            }
        }
        when(run.steps()).thenReturn(steps);
        List<SwingV1BacktestReport> reports = new ArrayList<>(experiment.reports());
        reports.set(0, report);
        when(experiment.reports()).thenReturn(reports);
        evaluations.set(1, evaluation(experiment));

        assertThatThrownBy(() -> new SwingV1CostSensitivityResult(sensitivity, evaluations))
                .isInstanceOf(field.equals("MISSING_EVIDENCE") ? NullPointerException.class : IllegalArgumentException.class);
    }

    private List<SwingV1BacktestExperimentEvaluation> evaluations() {
        return sensitivity.costModels().stream().map(sensitivity::requestFor).map(fixtures::evaluate).toList();
    }

    private SwingV1BacktestExperimentResult changedExperiment(SwingV1BacktestExperimentEvaluation evaluation) {
        SwingV1BacktestExperimentResult original = evaluation.experimentResult();
        SwingV1BacktestExperimentResult experiment = mock(SwingV1BacktestExperimentResult.class);
        when(experiment.request()).thenReturn(original.request());
        when(experiment.reports()).thenReturn(original.reports());
        when(experiment.benchmarkPerformanceSummary()).thenReturn(original.benchmarkPerformanceSummary());
        return experiment;
    }

    private SwingV1BacktestExperimentEvaluation evaluation(SwingV1BacktestExperimentResult experiment) {
        SwingV1BacktestExperimentEvaluation evaluation = mock(SwingV1BacktestExperimentEvaluation.class);
        when(evaluation.experimentResult()).thenReturn(experiment);
        return evaluation;
    }
}
