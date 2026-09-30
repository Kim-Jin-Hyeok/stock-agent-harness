package com.stock.backtest.strategy.swing.v1.report.trade.metric;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
                SwingV1ProfitFactor.profitWithoutLoss()
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
                SwingV1ProfitFactor.noCompletedTrades()
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
                SwingV1ProfitFactor.noProfitOrLoss()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Profit factor status must match trade outcomes."
                );
    }
}
