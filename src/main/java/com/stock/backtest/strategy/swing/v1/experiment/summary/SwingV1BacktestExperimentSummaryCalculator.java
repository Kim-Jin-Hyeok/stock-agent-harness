package com.stock.backtest.strategy.swing.v1.experiment.summary;

import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentResult;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class SwingV1BacktestExperimentSummaryCalculator {
    private static final MathContext RATE_MATH_CONTEXT =
            MathContext.DECIMAL128;
    private static final Comparator<ReportMetrics> RETURN_ORDER =
            Comparator.comparing(ReportMetrics::adjustedReturnRate)
                    .thenComparing(ReportMetrics::symbol);
    private static final Comparator<ReportMetrics> DRAWDOWN_ORDER =
            Comparator.comparing(ReportMetrics::maxDrawdownRate)
                    .reversed()
                    .thenComparing(ReportMetrics::symbol);

    public SwingV1BacktestExperimentSummary calculate(
            SwingV1BacktestExperimentResult experimentResult
    ) {
        Objects.requireNonNull(
                experimentResult,
                "experimentResult must not be null."
        );

        List<ReportMetrics> metrics = experimentResult.reports()
                .stream()
                .map(this::toMetrics)
                .toList();
        BigDecimal benchmarkReturnRate = experimentResult
                .benchmarkPerformanceSummary()
                .totalReturnRate();
        int profitableSymbolCount = 0;
        int losingSymbolCount = 0;
        int breakEvenSymbolCount = 0;
        int noCompletedTradeSymbolCount = 0;
        int totalCompletedTradeCount = 0;
        int benchmarkOutperformingSymbolCount = 0;

        for (ReportMetrics metric : metrics) {
            int returnSign = metric.adjustedReturnRate().signum();
            if (returnSign > 0) {
                profitableSymbolCount = Math.incrementExact(
                        profitableSymbolCount
                );
            } else if (returnSign < 0) {
                losingSymbolCount = Math.incrementExact(
                        losingSymbolCount
                );
            } else {
                breakEvenSymbolCount = Math.incrementExact(
                        breakEvenSymbolCount
                );
            }

            if (metric.completedTradeCount() == 0) {
                noCompletedTradeSymbolCount = Math.incrementExact(
                        noCompletedTradeSymbolCount
                );
            }
            totalCompletedTradeCount = Math.addExact(
                    totalCompletedTradeCount,
                    metric.completedTradeCount()
            );
            if (metric.adjustedReturnRate()
                    .compareTo(benchmarkReturnRate) > 0) {
                benchmarkOutperformingSymbolCount = Math.incrementExact(
                        benchmarkOutperformingSymbolCount
                );
            }
        }

        ReportMetrics worstReturn = metrics.stream()
                .min(RETURN_ORDER)
                .orElseThrow();
        ReportMetrics worstDrawdown = metrics.stream()
                .min(DRAWDOWN_ORDER)
                .orElseThrow();
        BigDecimal medianReturnRate = medianAdjustedReturnRate(metrics);
        return new SwingV1BacktestExperimentSummary(
                experimentResult.request(),
                metrics.size(),
                profitableSymbolCount,
                losingSymbolCount,
                breakEvenSymbolCount,
                noCompletedTradeSymbolCount,
                totalCompletedTradeCount,
                medianReturnRate,
                benchmarkReturnRate,
                medianReturnRate.subtract(benchmarkReturnRate),
                benchmarkOutperformingSymbolCount,
                worstReturn.symbol(),
                worstReturn.adjustedReturnRate(),
                worstDrawdown.symbol(),
                worstDrawdown.maxDrawdownRate()
        );
    }

    private ReportMetrics toMetrics(SwingV1BacktestReport report) {
        return new ReportMetrics(
                report.request().candidateSymbol(),
                report.terminalLiquidationEstimate()
                        .liquidationAdjustedTotalReturnRate(),
                report.performanceSummary().maxDrawdownRate(),
                report.tradePerformanceSummary().completedTradeCount()
        );
    }

    private BigDecimal medianAdjustedReturnRate(
            List<ReportMetrics> metrics
    ) {
        List<BigDecimal> sortedRates = new ArrayList<>(metrics.size());
        for (ReportMetrics metric : metrics) {
            sortedRates.add(metric.adjustedReturnRate());
        }
        sortedRates.sort(BigDecimal::compareTo);

        int middleIndex = sortedRates.size() / 2;
        if (sortedRates.size() % 2 == 1) {
            return sortedRates.get(middleIndex);
        }
        return sortedRates.get(middleIndex - 1)
                .add(sortedRates.get(middleIndex))
                .divide(BigDecimal.valueOf(2L), RATE_MATH_CONTEXT);
    }

    private record ReportMetrics(
            String symbol,
            BigDecimal adjustedReturnRate,
            BigDecimal maxDrawdownRate,
            int completedTradeCount
    ) {
    }
}
