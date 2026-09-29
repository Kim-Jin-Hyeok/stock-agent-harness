package com.stock.backtest.strategy.swing.v1.execution.result;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.backtest.execution.fill.daily.DailyOpenFillApproximation;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionResult;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionStatus;

import java.time.LocalDate;
import java.util.Objects;

public record SwingV1BacktestStepResult(
        SwingV1BacktestStepStatus status,
        LocalDate decisionDate,
        InvestmentDecision decision,
        DailyOpenFillApproximation fill,
        BacktestPortfolioTransitionResult portfolioTransition,
        BacktestPortfolioState portfolioStateBefore,
        BacktestPortfolioState portfolioStateAfter,
        String reason
) {
    public SwingV1BacktestStepResult {
        Objects.requireNonNull(status, "status must not be null.");
        Objects.requireNonNull(
                decisionDate,
                "decisionDate must not be null."
        );
        Objects.requireNonNull(decision, "decision must not be null.");
        Objects.requireNonNull(
                portfolioStateBefore,
                "portfolioStateBefore must not be null."
        );
        Objects.requireNonNull(
                portfolioStateAfter,
                "portfolioStateAfter must not be null."
        );
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank.");
        }

        validateStatusFields(
                status,
                decision,
                fill,
                portfolioTransition,
                portfolioStateBefore,
                portfolioStateAfter
        );
    }

    public static SwingV1BacktestStepResult hold(
            LocalDate decisionDate,
            InvestmentDecision decision,
            BacktestPortfolioState portfolioState
    ) {
        return new SwingV1BacktestStepResult(
                SwingV1BacktestStepStatus.HOLD,
                decisionDate,
                decision,
                null,
                null,
                portfolioState,
                portfolioState,
                decision.reason()
        );
    }

    public static SwingV1BacktestStepResult noNextDailyBar(
            LocalDate decisionDate,
            InvestmentDecision decision,
            BacktestPortfolioState portfolioState
    ) {
        return new SwingV1BacktestStepResult(
                SwingV1BacktestStepStatus.NO_NEXT_DAILY_BAR,
                decisionDate,
                decision,
                null,
                null,
                portfolioState,
                portfolioState,
                "Next daily price bar was not found. symbol="
                        + decision.symbol()
        );
    }

    public static SwingV1BacktestStepResult transitioned(
            LocalDate decisionDate,
            InvestmentDecision decision,
            DailyOpenFillApproximation fill,
            BacktestPortfolioTransitionResult portfolioTransition,
            BacktestPortfolioState portfolioStateBefore
    ) {
        Objects.requireNonNull(
                portfolioTransition,
                "portfolioTransition must not be null."
        );
        boolean applied = portfolioTransition.applied();
        return new SwingV1BacktestStepResult(
                applied
                        ? SwingV1BacktestStepStatus.EXECUTED
                        : SwingV1BacktestStepStatus.REJECTED,
                decisionDate,
                decision,
                fill,
                portfolioTransition,
                portfolioStateBefore,
                portfolioTransition.portfolioState(),
                applied
                        ? "Backtest step was executed."
                        : portfolioTransition.reason()
        );
    }

    private static void validateStatusFields(
            SwingV1BacktestStepStatus status,
            InvestmentDecision decision,
            DailyOpenFillApproximation fill,
            BacktestPortfolioTransitionResult portfolioTransition,
            BacktestPortfolioState portfolioStateBefore,
            BacktestPortfolioState portfolioStateAfter
    ) {
        switch (status) {
            case HOLD -> validateHold(
                    decision,
                    fill,
                    portfolioTransition,
                    portfolioStateBefore,
                    portfolioStateAfter
            );
            case NO_NEXT_DAILY_BAR -> validateNoNextDailyBar(
                    decision,
                    fill,
                    portfolioTransition,
                    portfolioStateBefore,
                    portfolioStateAfter
            );
            case EXECUTED -> validateTransition(
                    decision,
                    fill,
                    portfolioTransition,
                    portfolioStateAfter,
                    BacktestPortfolioTransitionStatus.APPLIED
            );
            case REJECTED -> {
                validateTransition(
                        decision,
                        fill,
                        portfolioTransition,
                        portfolioStateAfter,
                        BacktestPortfolioTransitionStatus.REJECTED
                );
                if (!portfolioStateAfter.equals(portfolioStateBefore)) {
                    throw new IllegalArgumentException(
                            "REJECTED result must preserve portfolio state."
                    );
                }
            }
        }
    }

    private static void validateHold(
            InvestmentDecision decision,
            DailyOpenFillApproximation fill,
            BacktestPortfolioTransitionResult portfolioTransition,
            BacktestPortfolioState portfolioStateBefore,
            BacktestPortfolioState portfolioStateAfter
    ) {
        if (decision.action() != InvestmentAction.HOLD) {
            throw new IllegalArgumentException(
                    "HOLD status requires HOLD decision."
            );
        }
        validateNoExecutionData(
                fill,
                portfolioTransition,
                portfolioStateBefore,
                portfolioStateAfter,
                "HOLD"
        );
    }

    private static void validateNoNextDailyBar(
            InvestmentDecision decision,
            DailyOpenFillApproximation fill,
            BacktestPortfolioTransitionResult portfolioTransition,
            BacktestPortfolioState portfolioStateBefore,
            BacktestPortfolioState portfolioStateAfter
    ) {
        validateOrderDecision(decision);
        validateNoExecutionData(
                fill,
                portfolioTransition,
                portfolioStateBefore,
                portfolioStateAfter,
                "NO_NEXT_DAILY_BAR"
        );
    }

    private static void validateNoExecutionData(
            DailyOpenFillApproximation fill,
            BacktestPortfolioTransitionResult portfolioTransition,
            BacktestPortfolioState portfolioStateBefore,
            BacktestPortfolioState portfolioStateAfter,
            String status
    ) {
        if (fill != null || portfolioTransition != null) {
            throw new IllegalArgumentException(
                    status + " result must not contain execution data."
            );
        }
        if (!portfolioStateAfter.equals(portfolioStateBefore)) {
            throw new IllegalArgumentException(
                    status + " result must preserve portfolio state."
            );
        }
    }

    private static void validateTransition(
            InvestmentDecision decision,
            DailyOpenFillApproximation fill,
            BacktestPortfolioTransitionResult portfolioTransition,
            BacktestPortfolioState portfolioStateAfter,
            BacktestPortfolioTransitionStatus expectedStatus
    ) {
        validateOrderDecision(decision);
        Objects.requireNonNull(fill, "fill must not be null.");
        Objects.requireNonNull(
                portfolioTransition,
                "portfolioTransition must not be null."
        );
        if (portfolioTransition.status() != expectedStatus) {
            throw new IllegalArgumentException(
                    "portfolioTransition status must match step status."
            );
        }
        if (!portfolioStateAfter.equals(
                portfolioTransition.portfolioState()
        )) {
            throw new IllegalArgumentException(
                    "portfolioStateAfter must match portfolioTransition."
            );
        }
        if (fill.tradeCostCalculation().action() != decision.action()
                || !fill.symbol().equals(decision.symbol())
                || fill.tradeCostCalculation().quantity()
                != decision.quantity()) {
            throw new IllegalArgumentException(
                    "fill must match investment decision."
            );
        }
    }

    private static void validateOrderDecision(
            InvestmentDecision decision
    ) {
        if (decision.action() == null
                || decision.action() == InvestmentAction.HOLD
                || decision.symbol() == null
                || decision.symbol().isBlank()
                || decision.quantity() == null
                || decision.quantity() <= 0
                || decision.expectedPriceKrw() == null
                || decision.expectedPriceKrw() <= 0) {
            throw new IllegalArgumentException(
                    "Order step requires valid BUY or SELL decision."
            );
        }
    }
}
