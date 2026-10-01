package com.stock.backtest.strategy.swing.v1.experiment.runner;

import com.stock.backtest.performance.benchmark.BacktestBenchmarkPerformanceSummary;
import com.stock.backtest.strategy.swing.v1.experiment.evaluation.SwingV1BacktestExperimentEvaluation;
import com.stock.backtest.strategy.swing.v1.experiment.evaluation.SwingV1BacktestExperimentEvaluationService;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentRequest;
import com.stock.backtest.strategy.swing.v1.experiment.runner.config.SwingV1BacktestManualRunProperties;
import com.stock.backtest.strategy.swing.v1.experiment.summary.SwingV1BacktestExperimentSummary;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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
    private final SwingV1BacktestManualRunProperties properties;

    public SwingV1BacktestManualRunner(
            SwingV1BacktestExperimentEvaluationService evaluationService,
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
    }

    @Override
    public void run(ApplicationArguments arguments) {
        SwingV1BacktestExperimentRequest request =
                new SwingV1BacktestExperimentRequest(
                        STRATEGY_IDENTITY,
                        properties.benchmarkId(),
                        properties.fromSignalDate(),
                        properties.toSignalDate(),
                        properties.initialCashAmountKrwPerSymbol(),
                        properties.costModel()
                );
        log.info(
                "SWING_V1 manual backtest started. strategyIdentity={}, "
                        + "benchmarkId={}, fromSignalDate={}, toSignalDate={}, "
                        + "initialCashAmountKrwPerSymbol={}, costModel={}",
                request.strategyIdentity(),
                request.benchmarkId(),
                request.fromSignalDate(),
                request.toSignalDate(),
                request.initialCashAmountKrwPerSymbol(),
                request.costModel()
        );

        SwingV1BacktestExperimentEvaluation evaluation =
                evaluationService.evaluate(request);
        SwingV1BacktestExperimentSummary summary = evaluation.summary();
        BacktestBenchmarkPerformanceSummary benchmark = evaluation
                .experimentResult()
                .benchmarkPerformanceSummary();
        log.info(
                "SWING_V1 manual backtest completed. benchmarkId={}, "
                        + "valuationFromDate={}, valuationToDate={}, "
                        + "observationCount={}, symbolCount={}, "
                        + "totalCompletedTradeCount={}, "
                        + "benchmarkTotalReturnRate={}, "
                        + "medianLiquidationAdjustedReturnRate={}, "
                        + "medianExcessReturnRate={}, "
                        + "benchmarkOutperformingSymbolCount={}",
                benchmark.benchmarkId(),
                benchmark.fromDate(),
                benchmark.toDate(),
                benchmark.observationCount(),
                summary.symbolCount(),
                summary.totalCompletedTradeCount(),
                summary.benchmarkTotalReturnRate(),
                summary.medianLiquidationAdjustedReturnRate(),
                summary.medianExcessReturnRate(),
                summary.benchmarkOutperformingSymbolCount()
        );
    }
}
