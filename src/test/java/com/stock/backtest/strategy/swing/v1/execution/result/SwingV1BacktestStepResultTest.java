package com.stock.backtest.strategy.swing.v1.execution.result;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingV1BacktestStepResultTest {
    private static final LocalDate DECISION_DATE =
            LocalDate.of(2026, 9, 29);
    private final BacktestPortfolioState portfolioState =
            BacktestPortfolioState.withCash(1_000_000L);

    @Test
    void createsHoldAndNoNextDailyBarResults() {
        SwingV1BacktestStepResult hold =
                SwingV1BacktestStepResult.hold(
                        DECISION_DATE,
                        new InvestmentDecision(
                                InvestmentAction.HOLD,
                                null,
                                null,
                                null,
                                "No signal."
                        ),
                        portfolioState
                );
        SwingV1BacktestStepResult noNextDailyBar =
                SwingV1BacktestStepResult.noNextDailyBar(
                        DECISION_DATE,
                        new InvestmentDecision(
                                InvestmentAction.BUY,
                                "005930",
                                1L,
                                70_000L,
                                "Buy signal."
                        ),
                        portfolioState
                );

        assertThat(hold.status()).isEqualTo(
                SwingV1BacktestStepStatus.HOLD
        );
        assertThat(noNextDailyBar.status()).isEqualTo(
                SwingV1BacktestStepStatus.NO_NEXT_DAILY_BAR
        );
        assertThat(hold.portfolioStateAfter()).isSameAs(portfolioState);
        assertThat(noNextDailyBar.portfolioStateAfter())
                .isSameAs(portfolioState);
    }

    @Test
    void rejectsHoldStatusWithOrderDecision() {
        InvestmentDecision orderDecision = new InvestmentDecision(
                InvestmentAction.BUY,
                "005930",
                1L,
                70_000L,
                "Buy signal."
        );

        assertThatThrownBy(() -> new SwingV1BacktestStepResult(
                SwingV1BacktestStepStatus.HOLD,
                DECISION_DATE,
                orderDecision,
                null,
                null,
                portfolioState,
                portfolioState,
                "Invalid hold."
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("HOLD status requires HOLD decision.");
    }
}
