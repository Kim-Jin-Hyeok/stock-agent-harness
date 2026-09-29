package com.stock.backtest.strategy.swing.v1.execution.result;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingV1BacktestStepResultTest {
    private static final LocalDate SIGNAL_DATE =
            LocalDate.of(2026, 9, 28);
    private static final LocalDate DECISION_DATE =
            LocalDate.of(2026, 9, 29);
    private final BacktestPortfolioState portfolioState =
            BacktestPortfolioState.withCash(1_000_000L);

    @Test
    void createsHoldResultForNextTradingDate() {
        InvestmentDecision decision = holdDecision();

        SwingV1BacktestStepResult result =
                SwingV1BacktestStepResult.hold(
                        SIGNAL_DATE,
                        DECISION_DATE,
                        decision,
                        portfolioState
                );

        assertThat(result.status()).isEqualTo(
                SwingV1BacktestStepStatus.HOLD
        );
        assertThat(result.signalDate()).isEqualTo(SIGNAL_DATE);
        assertThat(result.decisionDate()).isEqualTo(DECISION_DATE);
        assertThat(result.decision()).isSameAs(decision);
        assertThat(result.portfolioStateAfter()).isSameAs(portfolioState);
    }

    @Test
    void createsNoNextDailyBarResultBeforeDecision() {
        SwingV1BacktestStepResult result =
                SwingV1BacktestStepResult.noNextDailyBar(
                        SIGNAL_DATE,
                        "005930",
                        portfolioState
                );

        assertThat(result.status()).isEqualTo(
                SwingV1BacktestStepStatus.NO_NEXT_DAILY_BAR
        );
        assertThat(result.signalDate()).isEqualTo(SIGNAL_DATE);
        assertThat(result.decisionDate()).isNull();
        assertThat(result.decision()).isNull();
        assertThat(result.portfolioStateAfter()).isSameAs(portfolioState);
    }

    @Test
    void rejectsDecisionDateThatIsNotAfterSignalDate() {
        assertThatThrownBy(() -> SwingV1BacktestStepResult.hold(
                SIGNAL_DATE,
                SIGNAL_DATE,
                holdDecision(),
                portfolioState
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("decisionDate must be after signalDate.");
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
                SIGNAL_DATE,
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

    private InvestmentDecision holdDecision() {
        return new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                null,
                "No signal."
        );
    }
}
