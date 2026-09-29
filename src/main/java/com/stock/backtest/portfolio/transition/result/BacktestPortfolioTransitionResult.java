package com.stock.backtest.portfolio.transition.result;

import com.stock.backtest.portfolio.BacktestPortfolioState;

import java.util.Objects;

public record BacktestPortfolioTransitionResult(
        BacktestPortfolioTransitionStatus status,
        BacktestPortfolioTransitionReasonCode reasonCode,
        String reason,
        BacktestPortfolioState portfolioState
) {
    public BacktestPortfolioTransitionResult {
        Objects.requireNonNull(status, "status must not be null.");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null.");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank.");
        }
        Objects.requireNonNull(
                portfolioState,
                "portfolioState must not be null."
        );
        validateStatusReasonCode(status, reasonCode);
    }

    public static BacktestPortfolioTransitionResult applied(
            BacktestPortfolioState portfolioState
    ) {
        return new BacktestPortfolioTransitionResult(
                BacktestPortfolioTransitionStatus.APPLIED,
                BacktestPortfolioTransitionReasonCode.FILL_APPLIED,
                "Backtest fill was applied to portfolio.",
                portfolioState
        );
    }

    public static BacktestPortfolioTransitionResult rejected(
            BacktestPortfolioTransitionReasonCode reasonCode,
            String reason,
            BacktestPortfolioState portfolioState
    ) {
        return new BacktestPortfolioTransitionResult(
                BacktestPortfolioTransitionStatus.REJECTED,
                reasonCode,
                reason,
                portfolioState
        );
    }

    public boolean applied() {
        return status == BacktestPortfolioTransitionStatus.APPLIED;
    }

    private static void validateStatusReasonCode(
            BacktestPortfolioTransitionStatus status,
            BacktestPortfolioTransitionReasonCode reasonCode
    ) {
        if (status == BacktestPortfolioTransitionStatus.APPLIED
                && reasonCode
                != BacktestPortfolioTransitionReasonCode.FILL_APPLIED) {
            throw new IllegalArgumentException(
                    "APPLIED status requires FILL_APPLIED reasonCode."
            );
        }
        if (status == BacktestPortfolioTransitionStatus.REJECTED
                && reasonCode
                == BacktestPortfolioTransitionReasonCode.FILL_APPLIED) {
            throw new IllegalArgumentException(
                    "REJECTED status must not use FILL_APPLIED reasonCode."
            );
        }
    }
}
