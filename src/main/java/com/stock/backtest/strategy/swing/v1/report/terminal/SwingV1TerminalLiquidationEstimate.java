package com.stock.backtest.strategy.swing.v1.report.terminal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

public record SwingV1TerminalLiquidationEstimate(
        long initialEquityAmountKrw,
        long markToMarketFinalEquityAmountKrw,
        long estimatedLiquidationCostAmountKrw,
        long liquidationAdjustedFinalEquityAmountKrw,
        long liquidationAdjustedNetProfitAmountKrw,
        BigDecimal liquidationAdjustedTotalReturnRate
) {
    private static final MathContext RATE_MATH_CONTEXT =
            MathContext.DECIMAL128;

    public SwingV1TerminalLiquidationEstimate {
        if (initialEquityAmountKrw <= 0) {
            throw new IllegalArgumentException(
                    "initialEquityAmountKrw must be positive."
            );
        }
        if (markToMarketFinalEquityAmountKrw < 0) {
            throw new IllegalArgumentException(
                    "markToMarketFinalEquityAmountKrw must not be negative."
            );
        }
        if (estimatedLiquidationCostAmountKrw < 0) {
            throw new IllegalArgumentException(
                    "estimatedLiquidationCostAmountKrw must not be negative."
            );
        }
        long expectedAdjustedFinalEquity = Math.subtractExact(
                markToMarketFinalEquityAmountKrw,
                estimatedLiquidationCostAmountKrw
        );
        if (liquidationAdjustedFinalEquityAmountKrw < 0
                || liquidationAdjustedFinalEquityAmountKrw
                != expectedAdjustedFinalEquity) {
            throw new IllegalArgumentException(
                    "liquidationAdjustedFinalEquityAmountKrw must match "
                            + "mark-to-market equity after liquidation cost."
            );
        }
        if (liquidationAdjustedNetProfitAmountKrw != Math.subtractExact(
                liquidationAdjustedFinalEquityAmountKrw,
                initialEquityAmountKrw
        )) {
            throw new IllegalArgumentException(
                    "liquidationAdjustedNetProfitAmountKrw must match "
                            + "initial and adjusted final equity."
            );
        }
        Objects.requireNonNull(
                liquidationAdjustedTotalReturnRate,
                "liquidationAdjustedTotalReturnRate must not be null."
        );
        BigDecimal expectedReturnRate = BigDecimal
                .valueOf(liquidationAdjustedNetProfitAmountKrw)
                .divide(
                        BigDecimal.valueOf(initialEquityAmountKrw),
                        RATE_MATH_CONTEXT
                );
        if (liquidationAdjustedTotalReturnRate.compareTo(
                expectedReturnRate
        ) != 0) {
            throw new IllegalArgumentException(
                    "liquidationAdjustedTotalReturnRate must match "
                            + "adjusted net profit and initial equity."
            );
        }
    }
}
