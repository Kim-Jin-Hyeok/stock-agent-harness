package com.stock.backtest.strategy.swing.v1.report.trade.metric;

import com.stock.backtest.strategy.swing.v1.report.trade.SwingV1CompletedTrade;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;

@Component
public class SwingV1TradePerformanceCalculator {
    private static final MathContext RATE_MATH_CONTEXT =
            MathContext.DECIMAL128;

    public SwingV1TradePerformanceSummary calculate(
            List<SwingV1CompletedTrade> completedTrades
    ) {
        completedTrades = List.copyOf(Objects.requireNonNull(
                completedTrades,
                "completedTrades must not be null."
        ));
        if (completedTrades.isEmpty()) {
            return noCompletedTrades();
        }

        int winningTradeCount = 0;
        int losingTradeCount = 0;
        int breakEvenTradeCount = 0;
        long totalNetProfitLossAmountKrw = 0L;
        long totalWinningNetProfitAmountKrw = 0L;
        long totalLosingNetLossAmountKrw = 0L;

        for (SwingV1CompletedTrade completedTrade : completedTrades) {
            Objects.requireNonNull(
                    completedTrade,
                    "completedTrade must not be null."
            );
            long netProfitLossAmountKrw =
                    completedTrade.netProfitLossAmountKrw();
            totalNetProfitLossAmountKrw = Math.addExact(
                    totalNetProfitLossAmountKrw,
                    netProfitLossAmountKrw
            );
            if (netProfitLossAmountKrw > 0) {
                winningTradeCount = Math.incrementExact(winningTradeCount);
                totalWinningNetProfitAmountKrw = Math.addExact(
                        totalWinningNetProfitAmountKrw,
                        netProfitLossAmountKrw
                );
            } else if (netProfitLossAmountKrw < 0) {
                losingTradeCount = Math.incrementExact(losingTradeCount);
                totalLosingNetLossAmountKrw = Math.addExact(
                        totalLosingNetLossAmountKrw,
                        Math.negateExact(netProfitLossAmountKrw)
                );
            } else {
                breakEvenTradeCount = Math.incrementExact(
                        breakEvenTradeCount
                );
            }
        }

        int completedTradeCount = completedTrades.size();
        return new SwingV1TradePerformanceSummary(
                completedTradeCount,
                winningTradeCount,
                losingTradeCount,
                breakEvenTradeCount,
                totalNetProfitLossAmountKrw,
                totalWinningNetProfitAmountKrw,
                totalLosingNetLossAmountKrw,
                average(winningTradeCount, completedTradeCount),
                average(
                        totalNetProfitLossAmountKrw,
                        completedTradeCount
                ),
                winningTradeCount == 0
                        ? null
                        : average(
                                totalWinningNetProfitAmountKrw,
                                winningTradeCount
                        ),
                losingTradeCount == 0
                        ? null
                        : average(
                                totalLosingNetLossAmountKrw,
                                losingTradeCount
                        ),
                profitFactor(
                        winningTradeCount,
                        losingTradeCount,
                        totalWinningNetProfitAmountKrw,
                        totalLosingNetLossAmountKrw
                )
        );
    }

    private SwingV1TradePerformanceSummary noCompletedTrades() {
        return new SwingV1TradePerformanceSummary(
                0,
                0,
                0,
                0,
                0L,
                0L,
                0L,
                null,
                null,
                null,
                null,
                SwingV1ProfitFactor.noCompletedTrades()
        );
    }

    private SwingV1ProfitFactor profitFactor(
            int winningTradeCount,
            int losingTradeCount,
            long totalWinningNetProfitAmountKrw,
            long totalLosingNetLossAmountKrw
    ) {
        if (losingTradeCount > 0) {
            return SwingV1ProfitFactor.calculated(
                    average(
                            totalWinningNetProfitAmountKrw,
                            totalLosingNetLossAmountKrw
                    )
            );
        }
        if (winningTradeCount > 0) {
            return SwingV1ProfitFactor.profitWithoutLoss();
        }
        return SwingV1ProfitFactor.noProfitOrLoss();
    }

    private BigDecimal average(long amount, long count) {
        return BigDecimal.valueOf(amount).divide(
                BigDecimal.valueOf(count),
                RATE_MATH_CONTEXT
        );
    }
}
