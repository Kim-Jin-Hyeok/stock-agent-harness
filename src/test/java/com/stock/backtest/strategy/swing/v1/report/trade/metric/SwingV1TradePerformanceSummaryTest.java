package com.stock.backtest.strategy.swing.v1.report.trade.metric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.MathContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingV1TradePerformanceSummaryTest {

    @Test
    void rejectsCompletedCountDifferentFromClassifiedCounts() {
        assertThatThrownBy(() -> new SwingV1TradePerformanceSummary(
                2,
                1,
                0,
                0,
                1_000L,
                1_000L,
                0L,
                new BigDecimal("0.5"),
                new BigDecimal("500"),
                new BigDecimal("1000"),
                null,
                SwingV1ProfitFactor.profitWithoutLoss(),
                1_000L,
                BigDecimal.ONE,
                0L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Completed trade count must match classified counts."
                );
    }

    @Test
    void rejectsNoTradeSummaryContainingRates() {
        assertThatThrownBy(() -> new SwingV1TradePerformanceSummary(
                0,
                0,
                0,
                0,
                0L,
                0L,
                0L,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                SwingV1ProfitFactor.noCompletedTrades(),
                0L,
                null,
                0L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "No-trade summary must not contain win rate or "
                                + "average net profit."
                );
    }

    @Test
    void rejectsProfitFactorStatusDifferentFromTradeOutcomes() {
        assertThatThrownBy(() -> new SwingV1TradePerformanceSummary(
                1,
                1,
                0,
                0,
                1_000L,
                1_000L,
                0L,
                BigDecimal.ONE,
                new BigDecimal("1000"),
                new BigDecimal("1000"),
                null,
                SwingV1ProfitFactor.noProfitOrLoss(),
                1_000L,
                BigDecimal.ONE,
                0L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Profit factor status must match trade outcomes."
                );
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 0L, 1_499L, 3_001L})
    void rejectsLargestProfitOutsideWinningProfitBounds(long largestProfit) {
        assertThatThrownBy(() -> winningSummary(largestProfit, share(), 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Largest winning trade profit must be between "
                                + "average winning profit and total winning profit."
                );
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.1", "0.5", "1.1"})
    void rejectsShareDifferentFromLargestProfitDividedByWinningTotal(
            String invalidShare
    ) {
        assertThatThrownBy(() -> winningSummary(
                2_000L,
                new BigDecimal(invalidShare),
                0L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "largestWinningTradeProfitShare must match calculated value."
                );
    }

    @Test
    void rejectsMissingShareWhenWinningTradesExist() {
        assertThatThrownBy(() -> winningSummary(2_000L, null, 0L))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("largestWinningTradeProfitShare must not be null.");
    }

    @Test
    void rejectsExcludedProfitDifferentFromSubtractingOneLargestWinner() {
        assertThatThrownBy(() -> winningSummary(2_000L, share(), 500L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Net profit excluding largest winning trade must match "
                                + "total net profit minus one largest winning profit."
                );
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 1L})
    void rejectsNonZeroLargestProfitWithoutWinningTrades(long largestProfit) {
        assertThatThrownBy(() -> lossOnlySummary(largestProfit, null, -1_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "No winning trades require zero largest profit "
                                + "and no profit share."
                );
    }

    @Test
    void rejectsShareWithoutWinningTradesEvenWhenZero() {
        assertThatThrownBy(() -> lossOnlySummary(0L, BigDecimal.ZERO, -1_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "No winning trades require zero largest profit "
                                + "and no profit share."
                );
    }

    @Test
    void rejectsChangedNetProfitWithoutWinningTradeToExclude() {
        assertThatThrownBy(() -> lossOnlySummary(0L, null, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Net profit excluding largest winning trade must match "
                                + "total net profit minus one largest winning profit."
                );
    }

    @Test
    void acceptsEquivalentShareWithDifferentDecimalScale() {
        BigDecimal scaledShare = share().setScale(36);

        SwingV1TradePerformanceSummary summary = winningSummary(
                2_000L,
                scaledShare,
                0L
        );

        assertThat(summary.largestWinningTradeProfitShare())
                .isEqualByComparingTo(share());
    }

    private SwingV1TradePerformanceSummary winningSummary(
            long largestProfit,
            BigDecimal profitShare,
            long excludedProfit
    ) {
        return new SwingV1TradePerformanceSummary(
                3, 2, 1, 0,
                2_000L, 3_000L, 1_000L,
                share(),
                BigDecimal.valueOf(2_000L).divide(
                        BigDecimal.valueOf(3L), MathContext.DECIMAL128
                ),
                new BigDecimal("1500"),
                new BigDecimal("1000"),
                SwingV1ProfitFactor.calculated(new BigDecimal("3")),
                largestProfit,
                profitShare,
                excludedProfit
        );
    }

    private SwingV1TradePerformanceSummary lossOnlySummary(
            long largestProfit,
            BigDecimal profitShare,
            long excludedProfit
    ) {
        return new SwingV1TradePerformanceSummary(
                1, 0, 1, 0,
                -1_000L, 0L, 1_000L,
                BigDecimal.ZERO,
                new BigDecimal("-1000"),
                null,
                new BigDecimal("1000"),
                SwingV1ProfitFactor.calculated(BigDecimal.ZERO),
                largestProfit,
                profitShare,
                excludedProfit
        );
    }

    private BigDecimal share() {
        return BigDecimal.valueOf(2L).divide(
                BigDecimal.valueOf(3L), MathContext.DECIMAL128
        );
    }
}
