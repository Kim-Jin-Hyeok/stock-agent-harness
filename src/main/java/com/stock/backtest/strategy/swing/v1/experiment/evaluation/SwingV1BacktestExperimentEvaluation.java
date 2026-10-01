package com.stock.backtest.strategy.swing.v1.experiment.evaluation;

import com.stock.backtest.comparison.buyandhold.model.BuyAndHoldBacktestRequest;
import com.stock.backtest.comparison.buyandhold.model.BuyAndHoldBacktestResult;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentResult;
import com.stock.backtest.strategy.swing.v1.experiment.summary.SwingV1BacktestExperimentSummary;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SwingV1BacktestExperimentEvaluation(
        SwingV1BacktestExperimentResult experimentResult,
        SwingV1BacktestExperimentSummary summary,
        List<BuyAndHoldBacktestResult> buyAndHoldResults
) {
    public static final List<BigDecimal> BUY_AND_HOLD_INITIAL_ALLOCATION_RATIOS =
            List.of(BigDecimal.ONE, new BigDecimal("0.1"));

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
        buyAndHoldResults = List.copyOf(Objects.requireNonNull(
                buyAndHoldResults,
                "buyAndHoldResults must not be null."
        ));
        validateComparisons(experimentResult, buyAndHoldResults);
    }

    private static void validateComparisons(
            SwingV1BacktestExperimentResult experiment,
            List<BuyAndHoldBacktestResult> comparisons
    ) {
        if (comparisons.size() != Math.multiplyExact(
                experiment.reports().size(),
                BUY_AND_HOLD_INITIAL_ALLOCATION_RATIOS.size()
        )) {
            throw new IllegalArgumentException(
                    "Each report must have both fixed buy-and-hold allocations."
            );
        }
        for (SwingV1BacktestReport report : experiment.reports()) {
            String symbol = report.request().candidateSymbol();
            List<Instant> instants = report.runResult().equityCurve().stream()
                    .map(BacktestEquitySnapshot::evaluatedAt)
                    .toList();
            for (BigDecimal ratio : BUY_AND_HOLD_INITIAL_ALLOCATION_RATIOS) {
                List<BuyAndHoldBacktestResult> matches = comparisons.stream()
                        .filter(comparison -> symbol.equals(
                                comparison.request().symbol()
                        ) && ratio.compareTo(
                                comparison.request().initialAllocationRatio()
                        ) == 0)
                        .toList();
                if (matches.size() != 1) {
                    throw new IllegalArgumentException(
                            "Each symbol and fixed allocation must appear once."
                    );
                }
                BuyAndHoldBacktestRequest request = matches.getFirst().request();
                if (request.initialCashAmountKrw()
                        != report.request().initialPortfolioState().cashAmountKrw()
                        || !request.costModel().equals(report.request().costModel())
                        || !request.valuationInstants().equals(instants)) {
                    throw new IllegalArgumentException(
                            "Comparison cash, costModel and valuation instants "
                                    + "must match the SWING report."
                    );
                }
            }
        }
    }
}
