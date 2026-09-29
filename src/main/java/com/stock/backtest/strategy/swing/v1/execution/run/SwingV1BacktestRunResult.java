package com.stock.backtest.strategy.swing.v1.execution.run;

import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepResult;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepStatus;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record SwingV1BacktestRunResult(
        InvestmentStrategyIdentity strategyIdentity,
        String candidateSymbol,
        LocalDate fromSignalDate,
        LocalDate toSignalDate,
        BacktestPortfolioState initialPortfolioState,
        BacktestPortfolioState finalPortfolioState,
        List<SwingV1BacktestStepResult> steps
) {
    public SwingV1BacktestRunResult {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        if (candidateSymbol == null || candidateSymbol.isBlank()) {
            throw new IllegalArgumentException(
                    "candidateSymbol must not be blank."
            );
        }
        Objects.requireNonNull(
                fromSignalDate,
                "fromSignalDate must not be null."
        );
        Objects.requireNonNull(
                toSignalDate,
                "toSignalDate must not be null."
        );
        if (fromSignalDate.isAfter(toSignalDate)) {
            throw new IllegalArgumentException(
                    "fromSignalDate must not be after toSignalDate."
            );
        }
        Objects.requireNonNull(
                initialPortfolioState,
                "initialPortfolioState must not be null."
        );
        Objects.requireNonNull(
                finalPortfolioState,
                "finalPortfolioState must not be null."
        );
        steps = List.copyOf(Objects.requireNonNull(
                steps,
                "steps must not be null."
        ));
        validateSteps(
                fromSignalDate,
                toSignalDate,
                initialPortfolioState,
                finalPortfolioState,
                steps
        );
    }

    private static void validateSteps(
            LocalDate fromSignalDate,
            LocalDate toSignalDate,
            BacktestPortfolioState initialPortfolioState,
            BacktestPortfolioState finalPortfolioState,
            List<SwingV1BacktestStepResult> steps
    ) {
        BacktestPortfolioState expectedState = initialPortfolioState;
        LocalDate previousSignalDate = null;

        for (int index = 0; index < steps.size(); index++) {
            SwingV1BacktestStepResult step = Objects.requireNonNull(
                    steps.get(index),
                    "step must not be null."
            );
            LocalDate signalDate = step.signalDate();
            if (signalDate.isBefore(fromSignalDate)
                    || signalDate.isAfter(toSignalDate)) {
                throw new IllegalArgumentException(
                        "step signalDate must be within run range."
                );
            }
            if (previousSignalDate != null
                    && !signalDate.isAfter(previousSignalDate)) {
                throw new IllegalArgumentException(
                        "steps must be ordered by unique signalDate."
                );
            }
            if (!step.portfolioStateBefore().equals(expectedState)) {
                throw new IllegalArgumentException(
                        "step portfolio state must continue from previous "
                                + "step."
                );
            }
            if (step.status()
                    == SwingV1BacktestStepStatus.NO_NEXT_DAILY_BAR
                    && index != steps.size() - 1) {
                throw new IllegalArgumentException(
                        "NO_NEXT_DAILY_BAR must be the last step."
                );
            }

            expectedState = step.portfolioStateAfter();
            previousSignalDate = signalDate;
        }

        if (!finalPortfolioState.equals(expectedState)) {
            throw new IllegalArgumentException(
                    "finalPortfolioState must match the last step state."
            );
        }
    }
}
