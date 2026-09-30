package com.stock.backtest.strategy.swing.v1.report.trade.metric;

import java.math.BigDecimal;
import java.util.Objects;

public record SwingV1ProfitFactor(
        SwingV1ProfitFactorStatus status,
        BigDecimal value
) {
    public SwingV1ProfitFactor {
        Objects.requireNonNull(status, "status must not be null.");
        if (status == SwingV1ProfitFactorStatus.CALCULATED) {
            Objects.requireNonNull(
                    value,
                    "value must not be null for CALCULATED status."
            );
            if (value.signum() < 0) {
                throw new IllegalArgumentException(
                        "value must not be negative."
                );
            }
        } else if (value != null) {
            throw new IllegalArgumentException(
                    "value must be null when status is not CALCULATED."
            );
        }
    }

    public static SwingV1ProfitFactor calculated(BigDecimal value) {
        return new SwingV1ProfitFactor(
                SwingV1ProfitFactorStatus.CALCULATED,
                value
        );
    }

    public static SwingV1ProfitFactor noCompletedTrades() {
        return new SwingV1ProfitFactor(
                SwingV1ProfitFactorStatus.NO_COMPLETED_TRADES,
                null
        );
    }

    public static SwingV1ProfitFactor profitWithoutLoss() {
        return new SwingV1ProfitFactor(
                SwingV1ProfitFactorStatus.PROFIT_WITHOUT_LOSS,
                null
        );
    }

    public static SwingV1ProfitFactor noProfitOrLoss() {
        return new SwingV1ProfitFactor(
                SwingV1ProfitFactorStatus.NO_PROFIT_OR_LOSS,
                null
        );
    }
}
