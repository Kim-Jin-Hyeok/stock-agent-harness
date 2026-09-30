package com.stock.backtest.strategy.swing.v1.report.terminal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingV1TerminalLiquidationEstimateTest {

    @Test
    void rejectsAdjustedEquityThatDoesNotSubtractLiquidationCost() {
        assertThatThrownBy(() -> new SwingV1TerminalLiquidationEstimate(
                1_000_000L,
                1_100_000L,
                10_000L,
                1_100_000L,
                100_000L,
                new BigDecimal("0.1")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "liquidationAdjustedFinalEquityAmountKrw must match "
                                + "mark-to-market equity after liquidation "
                                + "cost."
                );
    }

    @Test
    void rejectsReturnRateThatDoesNotMatchAdjustedProfit() {
        assertThatThrownBy(() -> new SwingV1TerminalLiquidationEstimate(
                1_000_000L,
                1_100_000L,
                10_000L,
                1_090_000L,
                90_000L,
                new BigDecimal("0.1")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "liquidationAdjustedTotalReturnRate must match "
                                + "adjusted net profit and initial equity."
                );
    }
}
