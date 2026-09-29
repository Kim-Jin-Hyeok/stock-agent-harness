package com.stock.backtest.strategy.swing.v1.execution.run;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepResult;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingV1BacktestRunResultTest {
    private static final LocalDate FIRST_SIGNAL_DATE =
            LocalDate.of(2026, 9, 28);
    private static final LocalDate SECOND_SIGNAL_DATE =
            LocalDate.of(2026, 9, 29);
    private final BacktestPortfolioState portfolioState =
            BacktestPortfolioState.withCash(1_000_000L);

    @Test
    void createsResultWithOrderedContinuousSteps() {
        List<SwingV1BacktestStepResult> mutableSteps = new ArrayList<>(
                List.of(
                        hold(FIRST_SIGNAL_DATE, portfolioState),
                        hold(SECOND_SIGNAL_DATE, portfolioState)
                )
        );

        SwingV1BacktestRunResult result = result(
                portfolioState,
                mutableSteps
        );
        mutableSteps.clear();

        assertThat(result.steps()).hasSize(2);
        assertThat(result.finalPortfolioState())
                .isEqualTo(portfolioState);
    }

    @Test
    void rejectsDisconnectedPortfolioStateBetweenSteps() {
        BacktestPortfolioState unrelatedState =
                BacktestPortfolioState.withCash(900_000L);

        assertThatThrownBy(() -> result(
                unrelatedState,
                List.of(
                        hold(FIRST_SIGNAL_DATE, portfolioState),
                        hold(SECOND_SIGNAL_DATE, unrelatedState)
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "step portfolio state must continue from previous "
                                + "step."
                );
    }

    @Test
    void rejectsNoNextDailyBarBeforeLastStep() {
        SwingV1BacktestStepResult noNextDailyBar =
                SwingV1BacktestStepResult.noNextDailyBar(
                        FIRST_SIGNAL_DATE,
                        "005930",
                        portfolioState
                );

        assertThatThrownBy(() -> result(
                portfolioState,
                List.of(
                        noNextDailyBar,
                        hold(SECOND_SIGNAL_DATE, portfolioState)
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("NO_NEXT_DAILY_BAR must be the last step.");
    }

    private SwingV1BacktestRunResult result(
            BacktestPortfolioState finalPortfolioState,
            List<SwingV1BacktestStepResult> steps
    ) {
        return new SwingV1BacktestRunResult(
                swingV1Identity(),
                "005930",
                FIRST_SIGNAL_DATE,
                SECOND_SIGNAL_DATE,
                portfolioState,
                finalPortfolioState,
                steps
        );
    }

    private SwingV1BacktestStepResult hold(
            LocalDate signalDate,
            BacktestPortfolioState state
    ) {
        return SwingV1BacktestStepResult.hold(
                signalDate,
                signalDate.plusDays(1),
                new InvestmentDecision(
                        InvestmentAction.HOLD,
                        null,
                        null,
                        null,
                        "No signal."
                ),
                state
        );
    }

    private InvestmentStrategyIdentity swingV1Identity() {
        return new InvestmentStrategyIdentity(
                "SWING_V1",
                1,
                InvestmentHorizon.SWING
        );
    }
}
