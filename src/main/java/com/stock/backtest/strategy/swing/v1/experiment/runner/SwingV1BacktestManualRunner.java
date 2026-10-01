package com.stock.backtest.strategy.swing.v1.experiment.runner;

import com.stock.backtest.comparison.buyandhold.model.BuyAndHoldBacktestResult;
import com.stock.backtest.performance.benchmark.BacktestBenchmarkPerformanceSummary;
import com.stock.backtest.strategy.swing.v1.experiment.diagnostic.SwingV1BacktestDiagnosticSummary;
import com.stock.backtest.strategy.swing.v1.experiment.evaluation.SwingV1BacktestExperimentEvaluation;
import com.stock.backtest.strategy.swing.v1.experiment.evaluation.SwingV1BacktestExperimentEvaluationService;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentRequest;
import com.stock.backtest.strategy.swing.v1.experiment.runner.config.SwingV1BacktestManualRunProperties;
import com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost.SwingV1CostSensitivityRequest;
import com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost.SwingV1CostSensitivityResult;
import com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost.SwingV1CostSensitivityService;
import com.stock.backtest.strategy.swing.v1.experiment.summary.SwingV1BacktestExperimentSummary;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReport;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "backtest.swing-v1.experiment.manual",
        name = "enabled",
        havingValue = "true"
)
public class SwingV1BacktestManualRunner implements ApplicationRunner {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );

    private final SwingV1BacktestExperimentEvaluationService evaluationService;
    private final SwingV1CostSensitivityService costSensitivityService;
    private final SwingV1BacktestManualRunProperties properties;

    public SwingV1BacktestManualRunner(
            SwingV1BacktestExperimentEvaluationService evaluationService,
            SwingV1CostSensitivityService costSensitivityService,
            SwingV1BacktestManualRunProperties properties
    ) {
        this.evaluationService = Objects.requireNonNull(
                evaluationService,
                "evaluationService must not be null."
        );
        this.properties = Objects.requireNonNull(
                properties,
                "properties must not be null."
        );
        this.costSensitivityService = Objects.requireNonNull(
                costSensitivityService,
                "costSensitivityService must not be null."
        );
    }

    @Override
    public void run(ApplicationArguments arguments) {
        SwingV1BacktestExperimentRequest request =
                new SwingV1BacktestExperimentRequest(
                        STRATEGY_IDENTITY,
                        properties.candidateSymbols(),
                        properties.benchmarkId(),
                        properties.fromSignalDate(),
                        properties.toSignalDate(),
                        properties.initialCashAmountKrwPerSymbol(),
                        properties.costModel()
                );
        log.info(
                "SWING_V1 manual backtest started. strategyIdentity={}, "
                        + "candidateSymbols={}, benchmarkId={}, "
                        + "fromSignalDate={}, toSignalDate={}, "
                        + "initialCashAmountKrwPerSymbol={}, costModel={}",
                request.strategyIdentity(),
                request.candidateSymbols(),
                request.benchmarkId(),
                request.fromSignalDate(),
                request.toSignalDate(),
                request.initialCashAmountKrwPerSymbol(),
                request.costModel()
        );

        if (properties.slippageSensitivityEnabled()) {
            SwingV1CostSensitivityRequest sensitivityRequest =
                    SwingV1CostSensitivityRequest.forSlippageStress(request);
            log.info("SWING_V1 slippage sensitivity started. costModels={}",
                    sensitivityRequest.costModels());
            SwingV1CostSensitivityResult sensitivityResult =
                    costSensitivityService.evaluate(sensitivityRequest);
            sensitivityResult.evaluations().forEach(this::logEvaluation);
            log.info("SWING_V1 slippage sensitivity completed. scenarioCount={}",
                    sensitivityResult.evaluations().size());
        } else {
            logEvaluation(evaluationService.evaluate(request));
        }
    }

    private void logEvaluation(SwingV1BacktestExperimentEvaluation evaluation) {
        SwingV1BacktestExperimentSummary summary = evaluation.summary();
        BacktestBenchmarkPerformanceSummary benchmark = evaluation
                .experimentResult()
                .benchmarkPerformanceSummary();
        List<SwingV1BacktestDiagnosticSummary> diagnostics = evaluation
                .experimentResult()
                .reports()
                .stream()
                .map(report -> SwingV1BacktestDiagnosticSummary.from(
                        report.runResult()
                ))
                .toList();
        logBuyAndHoldComparisons(evaluation);
        log.info(
                "SWING_V1 manual backtest completed. benchmarkId={}, "
                        + "valuationFromDate={}, valuationToDate={}, "
                        + "observationCount={}, symbolCount={}, "
                        + "totalCompletedTradeCount={}, "
                        + "benchmarkTotalReturnRate={}, "
                        + "medianLiquidationAdjustedReturnRate={}, "
                        + "medianExcessReturnRate={}, "
                        + "benchmarkOutperformingSymbolCount={}, costModel={}",
                benchmark.benchmarkId(),
                benchmark.fromDate(),
                benchmark.toDate(),
                benchmark.observationCount(),
                summary.symbolCount(),
                summary.totalCompletedTradeCount(),
                summary.benchmarkTotalReturnRate(),
                summary.medianLiquidationAdjustedReturnRate(),
                summary.medianExcessReturnRate(),
                summary.benchmarkOutperformingSymbolCount(),
                evaluation.experimentResult().request().costModel()
        );
        diagnostics.forEach(diagnostic -> {
            log.info(
                    "SWING_V1 manual backtest diagnostic. symbol={}, "
                            + "stepStatusCounts={}, actionReasonCounts={}, "
                            + "blockedOrderReasonCounts={}, "
                            + "rejectedTransitionReasonCounts={}, "
                            + "executedBuyCount={}, executedSellCount={}, "
                            + "finalPositionQuantity={}, costModel={}",
                    diagnostic.symbol(),
                    diagnostic.stepStatusCounts(),
                    diagnostic.actionReasonCounts(),
                    diagnostic.blockedOrderReasonCounts(),
                    diagnostic.rejectedTransitionReasonCounts(),
                    diagnostic.executedBuyCount(),
                    diagnostic.executedSellCount(),
                    diagnostic.finalPositionQuantity(),
                    evaluation.experimentResult().request().costModel()
            );
        });
    }

    private void logBuyAndHoldComparisons(
            SwingV1BacktestExperimentEvaluation evaluation
    ) {
        for (BuyAndHoldBacktestResult comparison : evaluation.buyAndHoldResults()) {
            SwingV1BacktestReport report = evaluation.experimentResult().reports()
                    .stream()
                    .filter(candidate -> candidate.request().candidateSymbol()
                            .equals(comparison.request().symbol()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Buy-and-hold comparison must have a matching SWING report."
                    ));
            log.info(
                    "SWING_V1 manual backtest buy-and-hold comparison. symbol={}, "
                            + "initialAllocationRatio={}, buyBudgetAmountKrw={}, "
                            + "boughtQuantity={}, residualCashAmountKrw={}, "
                            + "buyAndHoldMarkToMarketReturnRate={}, "
                            + "buyAndHoldTerminalLiquidationCostAmountKrw={}, "
                            + "buyAndHoldLiquidationAdjustedReturnRate={}, "
                            + "buyAndHoldMaxDrawdownRate={}, "
                            + "swingLiquidationAdjustedReturnRate={}, "
                            + "swingMaxDrawdownRate={}, "
                            + "swingExcessReturnRateVsBuyAndHold={}, "
                            + "swingExposureSummary={}, buyAndHoldExposureSummary={}, "
                            + "swingCompletedTradeCount={}, swingFinalPositionQuantity={}, "
                            + "costModel={}",
                    comparison.request().symbol(),
                    comparison.request().initialAllocationRatio(),
                    comparison.request().buyBudgetAmountKrw(),
                    comparison.boughtQuantity(),
                    comparison.finalPortfolioState().cashAmountKrw(),
                    comparison.performanceSummary().totalReturnRate(),
                    comparison.estimatedTerminalLiquidationCostAmountKrw(),
                    comparison.liquidationAdjustedTotalReturnRate(),
                    comparison.performanceSummary().maxDrawdownRate(),
                    report.terminalLiquidationEstimate()
                            .liquidationAdjustedTotalReturnRate(),
                    report.performanceSummary().maxDrawdownRate(),
                    report.terminalLiquidationEstimate()
                            .liquidationAdjustedTotalReturnRate()
                            .subtract(comparison.liquidationAdjustedTotalReturnRate()),
                    report.exposureSummary(),
                    comparison.exposureSummary(),
                    report.completedTrades().size(),
                    report.runResult().finalPortfolioState().positions().stream()
                            .filter(position -> position.symbol().equals(comparison.request().symbol()))
                            .mapToLong(position -> position.quantity())
                            .sum(),
                    comparison.request().costModel()
            );
        }
    }
}
