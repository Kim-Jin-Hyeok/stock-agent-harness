package com.stock.backtest.strategy.swing.v1.report.trade.metric;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

public record SwingV1TradePerformanceSummary(
        int completedTradeCount,
        int winningTradeCount,
        int losingTradeCount,
        int breakEvenTradeCount,
        long totalNetProfitLossAmountKrw,
        long totalWinningNetProfitAmountKrw,
        long totalLosingNetLossAmountKrw,
        BigDecimal winRate,
        BigDecimal averageNetProfitLossAmountKrw,
        BigDecimal averageWinningNetProfitAmountKrw,
        BigDecimal averageLosingNetLossAmountKrw,
        SwingV1ProfitFactor profitFactor,
        long largestWinningTradeNetProfitAmountKrw,
        BigDecimal largestWinningTradeProfitShare,
        long netProfitLossExcludingLargestWinningTradeAmountKrw
) {
    private static final MathContext RATE_MATH_CONTEXT =
            MathContext.DECIMAL128;

    public SwingV1TradePerformanceSummary {
        validateCounts(
                completedTradeCount,
                winningTradeCount,
                losingTradeCount,
                breakEvenTradeCount
        );
        validateAmounts(
                totalNetProfitLossAmountKrw,
                totalWinningNetProfitAmountKrw,
                totalLosingNetLossAmountKrw
        );
        validateCategoryAverage(
                "winning",
                winningTradeCount,
                totalWinningNetProfitAmountKrw,
                averageWinningNetProfitAmountKrw
        );
        validateCategoryAverage(
                "losing",
                losingTradeCount,
                totalLosingNetLossAmountKrw,
                averageLosingNetLossAmountKrw
        );
        validateProfitConcentration(
                winningTradeCount,
                totalNetProfitLossAmountKrw,
                totalWinningNetProfitAmountKrw,
                largestWinningTradeNetProfitAmountKrw,
                largestWinningTradeProfitShare,
                netProfitLossExcludingLargestWinningTradeAmountKrw
        );
        Objects.requireNonNull(
                profitFactor,
                "profitFactor must not be null."
        );

        if (completedTradeCount == 0) {
            if (winRate != null
                    || averageNetProfitLossAmountKrw != null) {
                throw new IllegalArgumentException(
                        "No-trade summary must not contain win rate or "
                                + "average net profit."
                );
            }
            if (profitFactor.status()
                    != SwingV1ProfitFactorStatus.NO_COMPLETED_TRADES) {
                throw new IllegalArgumentException(
                        "No-trade summary requires NO_COMPLETED_TRADES "
                                + "profit factor status."
                );
            }
        } else {
            validateRate(
                    "winRate",
                    winRate,
                    BigDecimal.valueOf(winningTradeCount).divide(
                            BigDecimal.valueOf(completedTradeCount),
                            RATE_MATH_CONTEXT
                    )
            );
            validateRate(
                    "averageNetProfitLossAmountKrw",
                    averageNetProfitLossAmountKrw,
                    average(
                            totalNetProfitLossAmountKrw,
                            completedTradeCount
                    )
            );
            validateProfitFactor(
                    winningTradeCount,
                    losingTradeCount,
                    totalWinningNetProfitAmountKrw,
                    totalLosingNetLossAmountKrw,
                    profitFactor
            );
        }
    }

    private static void validateCounts(
            int completedTradeCount,
            int winningTradeCount,
            int losingTradeCount,
            int breakEvenTradeCount
    ) {
        if (completedTradeCount < 0
                || winningTradeCount < 0
                || losingTradeCount < 0
                || breakEvenTradeCount < 0) {
            throw new IllegalArgumentException(
                    "Trade counts must not be negative."
            );
        }
        int classifiedTradeCount = Math.addExact(
                Math.addExact(winningTradeCount, losingTradeCount),
                breakEvenTradeCount
        );
        if (completedTradeCount != classifiedTradeCount) {
            throw new IllegalArgumentException(
                    "Completed trade count must match classified counts."
            );
        }
    }

    private static void validateAmounts(
            long totalNetProfitLossAmountKrw,
            long totalWinningNetProfitAmountKrw,
            long totalLosingNetLossAmountKrw
    ) {
        if (totalWinningNetProfitAmountKrw < 0
                || totalLosingNetLossAmountKrw < 0) {
            throw new IllegalArgumentException(
                    "Winning profit and losing loss totals must not be "
                            + "negative."
            );
        }
        if (totalNetProfitLossAmountKrw != Math.subtractExact(
                totalWinningNetProfitAmountKrw,
                totalLosingNetLossAmountKrw
        )) {
            throw new IllegalArgumentException(
                    "Total net profit must match winning profit minus "
                            + "losing loss."
            );
        }
    }

    private static void validateCategoryAverage(
            String category,
            int tradeCount,
            long totalAmountKrw,
            BigDecimal averageAmountKrw
    ) {
        if (tradeCount == 0) {
            if (totalAmountKrw != 0 || averageAmountKrw != null) {
                throw new IllegalArgumentException(
                        "Empty " + category + " category must have zero "
                                + "total and no average."
                );
            }
            return;
        }
        if (totalAmountKrw <= 0) {
            throw new IllegalArgumentException(
                    "Non-empty " + category
                            + " category must have positive total."
            );
        }
        validateRate(
                "average " + category + " amount",
                averageAmountKrw,
                average(totalAmountKrw, tradeCount)
        );
    }

    private static void validateProfitConcentration(
            int winningTradeCount,
            long totalNetProfitLossAmountKrw,
            long totalWinningNetProfitAmountKrw,
            long largestWinningTradeNetProfitAmountKrw,
            BigDecimal largestWinningTradeProfitShare,
            long netProfitLossExcludingLargestWinningTradeAmountKrw
    ) {
        if (winningTradeCount == 0) {
            if (largestWinningTradeNetProfitAmountKrw != 0
                    || largestWinningTradeProfitShare != null) {
                throw new IllegalArgumentException(
                        "No winning trades require zero largest profit "
                                + "and no profit share."
                );
            }
        } else {
            BigDecimal largestProfit = BigDecimal.valueOf(
                    largestWinningTradeNetProfitAmountKrw
            );
            BigDecimal averageWinningProfit = average(
                    totalWinningNetProfitAmountKrw,
                    winningTradeCount
            );
            if (largestWinningTradeNetProfitAmountKrw <= 0
                    || largestWinningTradeNetProfitAmountKrw
                    > totalWinningNetProfitAmountKrw
                    || largestProfit.compareTo(averageWinningProfit) < 0) {
                throw new IllegalArgumentException(
                        "Largest winning trade profit must be between "
                                + "average winning profit and total winning profit."
                );
            }
            validateRate(
                    "largestWinningTradeProfitShare",
                    largestWinningTradeProfitShare,
                    largestProfit.divide(
                            BigDecimal.valueOf(totalWinningNetProfitAmountKrw),
                            RATE_MATH_CONTEXT
                    )
            );
        }
        if (netProfitLossExcludingLargestWinningTradeAmountKrw
                != Math.subtractExact(
                        totalNetProfitLossAmountKrw,
                        largestWinningTradeNetProfitAmountKrw
                )) {
            throw new IllegalArgumentException(
                    "Net profit excluding largest winning trade must match "
                            + "total net profit minus one largest winning profit."
            );
        }
    }

    private static void validateProfitFactor(
            int winningTradeCount,
            int losingTradeCount,
            long totalWinningNetProfitAmountKrw,
            long totalLosingNetLossAmountKrw,
            SwingV1ProfitFactor profitFactor
    ) {
        if (losingTradeCount > 0) {
            if (profitFactor.status()
                    != SwingV1ProfitFactorStatus.CALCULATED) {
                throw new IllegalArgumentException(
                        "Loss trades require CALCULATED profit factor."
                );
            }
            validateRate(
                    "profitFactor.value",
                    profitFactor.value(),
                    BigDecimal.valueOf(totalWinningNetProfitAmountKrw)
                            .divide(
                                    BigDecimal.valueOf(
                                            totalLosingNetLossAmountKrw
                                    ),
                                    RATE_MATH_CONTEXT
                            )
            );
            return;
        }

        SwingV1ProfitFactorStatus expectedStatus = winningTradeCount > 0
                ? SwingV1ProfitFactorStatus.PROFIT_WITHOUT_LOSS
                : SwingV1ProfitFactorStatus.NO_PROFIT_OR_LOSS;
        if (profitFactor.status() != expectedStatus) {
            throw new IllegalArgumentException(
                    "Profit factor status must match trade outcomes."
            );
        }
    }

    private static void validateRate(
            String fieldName,
            BigDecimal actual,
            BigDecimal expected
    ) {
        Objects.requireNonNull(actual, fieldName + " must not be null.");
        if (actual.compareTo(expected) != 0) {
            throw new IllegalArgumentException(
                    fieldName + " must match calculated value."
            );
        }
    }

    private static BigDecimal average(long amountKrw, int count) {
        return BigDecimal.valueOf(amountKrw).divide(
                BigDecimal.valueOf(count),
                RATE_MATH_CONTEXT
        );
    }
}
