package com.stock.backtest.portfolio.transition.result;

import com.stock.backtest.portfolio.BacktestPortfolioState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BacktestPortfolioTransitionResultTest {
    private final BacktestPortfolioState portfolioState =
            BacktestPortfolioState.withCash(1_000_000L);

    @Test
    void createsAppliedAndRejectedResults() {
        BacktestPortfolioTransitionResult applied =
                BacktestPortfolioTransitionResult.applied(portfolioState);
        BacktestPortfolioTransitionResult rejected =
                BacktestPortfolioTransitionResult.rejected(
                        BacktestPortfolioTransitionReasonCode
                                .INSUFFICIENT_CASH,
                        "Insufficient cash.",
                        portfolioState
                );

        assertThat(applied.status())
                .isEqualTo(BacktestPortfolioTransitionStatus.APPLIED);
        assertThat(applied.reasonCode())
                .isEqualTo(BacktestPortfolioTransitionReasonCode.FILL_APPLIED);
        assertThat(applied.applied()).isTrue();
        assertThat(rejected.status())
                .isEqualTo(BacktestPortfolioTransitionStatus.REJECTED);
        assertThat(rejected.reasonCode()).isEqualTo(
                BacktestPortfolioTransitionReasonCode.INSUFFICIENT_CASH
        );
        assertThat(rejected.applied()).isFalse();
        assertThat(rejected.portfolioState()).isSameAs(portfolioState);
    }

    @Test
    void rejectsStatusAndReasonCodeMismatch() {
        assertThatThrownBy(() -> new BacktestPortfolioTransitionResult(
                BacktestPortfolioTransitionStatus.APPLIED,
                BacktestPortfolioTransitionReasonCode.INSUFFICIENT_CASH,
                "Invalid result.",
                portfolioState
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "APPLIED status requires FILL_APPLIED reasonCode."
                );
        assertThatThrownBy(() -> new BacktestPortfolioTransitionResult(
                BacktestPortfolioTransitionStatus.REJECTED,
                BacktestPortfolioTransitionReasonCode.FILL_APPLIED,
                "Invalid result.",
                portfolioState
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "REJECTED status must not use FILL_APPLIED reasonCode."
                );
    }
}
