package com.stock.backtest.strategy.swing.v1.experiment.summary;

import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentRequest;

import java.math.BigDecimal;
import java.util.Objects;

public record SwingV1BacktestExperimentSummary(
        SwingV1BacktestExperimentRequest request,
        int symbolCount,
        int profitableSymbolCount,
        int losingSymbolCount,
        int breakEvenSymbolCount,
        int noCompletedTradeSymbolCount,
        int totalCompletedTradeCount,
        BigDecimal medianLiquidationAdjustedReturnRate,
        BigDecimal benchmarkTotalReturnRate,
        BigDecimal medianExcessReturnRate,
        int benchmarkOutperformingSymbolCount,
        String worstReturnSymbol,
        BigDecimal worstLiquidationAdjustedReturnRate,
        String worstDrawdownSymbol,
        BigDecimal worstMaxDrawdownRate
) {
    private static final BigDecimal MINIMUM_RETURN_RATE =
            BigDecimal.ONE.negate();

    public SwingV1BacktestExperimentSummary {
        Objects.requireNonNull(request, "request must not be null.");
        validateCounts(
                symbolCount,
                profitableSymbolCount,
                losingSymbolCount,
                breakEvenSymbolCount,
                noCompletedTradeSymbolCount,
                totalCompletedTradeCount,
                benchmarkOutperformingSymbolCount
        );
        validateReturnRates(
                medianLiquidationAdjustedReturnRate,
                worstLiquidationAdjustedReturnRate,
                benchmarkTotalReturnRate,
                medianExcessReturnRate
        );
        validateSymbol("worstReturnSymbol", worstReturnSymbol);
        validateSymbol("worstDrawdownSymbol", worstDrawdownSymbol);
        Objects.requireNonNull(
                worstMaxDrawdownRate,
                "worstMaxDrawdownRate must not be null."
        );
        if (worstMaxDrawdownRate.signum() < 0
                || worstMaxDrawdownRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "worstMaxDrawdownRate must be between 0 and 1."
            );
        }
    }

    private static void validateCounts(
            int symbolCount,
            int profitableSymbolCount,
            int losingSymbolCount,
            int breakEvenSymbolCount,
            int noCompletedTradeSymbolCount,
            int totalCompletedTradeCount,
            int benchmarkOutperformingSymbolCount
    ) {
        if (symbolCount < 1) {
            throw new IllegalArgumentException(
                    "symbolCount must be at least 1."
            );
        }
        if (profitableSymbolCount < 0
                || losingSymbolCount < 0
                || breakEvenSymbolCount < 0) {
            throw new IllegalArgumentException(
                    "Symbol outcome counts must not be negative."
            );
        }
        int classifiedSymbolCount = Math.addExact(
                Math.addExact(
                        profitableSymbolCount,
                        losingSymbolCount
                ),
                breakEvenSymbolCount
        );
        if (symbolCount != classifiedSymbolCount) {
            throw new IllegalArgumentException(
                    "symbolCount must match classified symbol counts."
            );
        }
        if (noCompletedTradeSymbolCount < 0
                || noCompletedTradeSymbolCount > symbolCount) {
            throw new IllegalArgumentException(
                    "noCompletedTradeSymbolCount must be between 0 and "
                            + "symbolCount."
            );
        }
        if (totalCompletedTradeCount < 0) {
            throw new IllegalArgumentException(
                    "totalCompletedTradeCount must not be negative."
            );
        }
        if (benchmarkOutperformingSymbolCount < 0
                || benchmarkOutperformingSymbolCount > symbolCount) {
            throw new IllegalArgumentException(
                    "benchmarkOutperformingSymbolCount must be between "
                            + "0 and symbolCount."
            );
        }
        boolean allSymbolsHaveNoCompletedTrades =
                noCompletedTradeSymbolCount == symbolCount;
        if (allSymbolsHaveNoCompletedTrades
                != (totalCompletedTradeCount == 0)) {
            throw new IllegalArgumentException(
                    "Completed trade counts must match no-trade symbols."
            );
        }
    }

    private static void validateReturnRates(
            BigDecimal medianReturnRate,
            BigDecimal worstReturnRate,
            BigDecimal benchmarkReturnRate,
            BigDecimal medianExcessReturnRate
    ) {
        Objects.requireNonNull(
                medianReturnRate,
                "medianLiquidationAdjustedReturnRate must not be null."
        );
        Objects.requireNonNull(
                worstReturnRate,
                "worstLiquidationAdjustedReturnRate must not be null."
        );
        if (medianReturnRate.compareTo(MINIMUM_RETURN_RATE) < 0
                || worstReturnRate.compareTo(MINIMUM_RETURN_RATE) < 0) {
            throw new IllegalArgumentException(
                    "Liquidation-adjusted return rates must not be less "
                            + "than -1."
            );
        }
        if (worstReturnRate.compareTo(medianReturnRate) > 0) {
            throw new IllegalArgumentException(
                    "worstLiquidationAdjustedReturnRate must not exceed "
                            + "the median."
            );
        }
        Objects.requireNonNull(
                benchmarkReturnRate,
                "benchmarkTotalReturnRate must not be null."
        );
        if (benchmarkReturnRate.compareTo(MINIMUM_RETURN_RATE) < 0) {
            throw new IllegalArgumentException(
                    "benchmarkTotalReturnRate must not be less than -1."
            );
        }
        Objects.requireNonNull(
                medianExcessReturnRate,
                "medianExcessReturnRate must not be null."
        );
        if (medianExcessReturnRate.compareTo(
                medianReturnRate.subtract(benchmarkReturnRate)
        ) != 0) {
            throw new IllegalArgumentException(
                    "medianExcessReturnRate must match the median "
                            + "return minus benchmark return."
            );
        }
    }

    private static void validateSymbol(
            String fieldName,
            String symbol
    ) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank."
            );
        }
    }
}
