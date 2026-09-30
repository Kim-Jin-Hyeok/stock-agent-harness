package com.stock.backtest.strategy.swing.v1.execution.result;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.backtest.execution.fill.daily.DailyOpenFillApproximation;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionResult;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionStatus;

import java.time.LocalDate;
import java.util.Objects;

public record SwingV1BacktestStepResult(
        SwingV1BacktestStepStatus status,
        LocalDate signalDate,
        LocalDate decisionDate,
        InvestmentDecision decision,
        DailyOpenFillApproximation fill,
        BacktestPortfolioTransitionResult portfolioTransition,
        BacktestPortfolioState portfolioStateBefore,
        BacktestPortfolioState portfolioStateAfter,
        BacktestEquitySnapshot equitySnapshot,
        String reason
) {
    public SwingV1BacktestStepResult {
        Objects.requireNonNull(status, "status must not be null.");
        Objects.requireNonNull(signalDate, "signalDate must not be null.");
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
                signalDate,
                decisionDate,
                decision,
                fill,
                portfolioTransition,
                portfolioStateBefore,
                portfolioStateAfter,
                equitySnapshot
        );
    }

    public static SwingV1BacktestStepResult noNextDailyBar(
            LocalDate signalDate,
            String symbol,
            BacktestPortfolioState portfolioState
    ) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        return new SwingV1BacktestStepResult(
                SwingV1BacktestStepStatus.NO_NEXT_DAILY_BAR,
                signalDate,
                null,
                null,
                null,
                null,
                portfolioState,
                portfolioState,
                null,
                "Next daily price bar was not found. symbol=" + symbol
        );
    }

    public static SwingV1BacktestStepResult hold(
            LocalDate signalDate,
            LocalDate decisionDate,
            InvestmentDecision decision,
            BacktestPortfolioState portfolioState,
            BacktestEquitySnapshot equitySnapshot
    ) {
        return new SwingV1BacktestStepResult(
                SwingV1BacktestStepStatus.HOLD,
                signalDate,
                decisionDate,
                decision,
                null,
                null,
                portfolioState,
                portfolioState,
                equitySnapshot,
                decision.reason()
        );
    }

    public static SwingV1BacktestStepResult transitioned(
            LocalDate signalDate,
            LocalDate decisionDate,
            InvestmentDecision decision,
            DailyOpenFillApproximation fill,
            BacktestPortfolioTransitionResult portfolioTransition,
            BacktestPortfolioState portfolioStateBefore,
            BacktestEquitySnapshot equitySnapshot
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
                signalDate,
                decisionDate,
                decision,
                fill,
                portfolioTransition,
                portfolioStateBefore,
                portfolioTransition.portfolioState(),
                equitySnapshot,
                applied
                        ? "Backtest step was executed."
                        : portfolioTransition.reason()
        );
    }

    private static void validateStatusFields(
            SwingV1BacktestStepStatus status,
            LocalDate signalDate,
            LocalDate decisionDate,
            InvestmentDecision decision,
            DailyOpenFillApproximation fill,
            BacktestPortfolioTransitionResult portfolioTransition,
            BacktestPortfolioState portfolioStateBefore,
            BacktestPortfolioState portfolioStateAfter,
            BacktestEquitySnapshot equitySnapshot
    ) {
        if (status == SwingV1BacktestStepStatus.NO_NEXT_DAILY_BAR) {
            validateNoNextDailyBar(
                    decisionDate,
                    decision,
                    fill,
                    portfolioTransition,
                    portfolioStateBefore,
                    portfolioStateAfter,
                    equitySnapshot
            );
            return;
        }

        Objects.requireNonNull(
                decisionDate,
                "decisionDate must not be null."
        );
        Objects.requireNonNull(decision, "decision must not be null.");
        if (!decisionDate.isAfter(signalDate)) {
            throw new IllegalArgumentException(
                    "decisionDate must be after signalDate."
            );
        }
        validateEquitySnapshot(
                decisionDate,
                portfolioStateAfter,
                equitySnapshot
        );

        switch (status) {
            case HOLD -> validateHold(
                    decision,
                    fill,
                    portfolioTransition,
                    portfolioStateBefore,
                    portfolioStateAfter
            );
            case EXECUTED -> validateTransition(
                    signalDate,
                    decisionDate,
                    decision,
                    fill,
                    portfolioTransition,
                    portfolioStateAfter,
                    BacktestPortfolioTransitionStatus.APPLIED
            );
            case REJECTED -> {
                validateTransition(
                        signalDate,
                        decisionDate,
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
            case NO_NEXT_DAILY_BAR -> throw new IllegalStateException(
                    "NO_NEXT_DAILY_BAR must already be validated."
            );
        }
    }

    private static void validateNoNextDailyBar(
            LocalDate decisionDate,
            InvestmentDecision decision,
            DailyOpenFillApproximation fill,
            BacktestPortfolioTransitionResult portfolioTransition,
            BacktestPortfolioState portfolioStateBefore,
            BacktestPortfolioState portfolioStateAfter,
            BacktestEquitySnapshot equitySnapshot
    ) {
        if (decisionDate != null
                || decision != null
                || fill != null
                || portfolioTransition != null
                || equitySnapshot != null) {
            throw new IllegalArgumentException(
                    "NO_NEXT_DAILY_BAR result must not contain decision, "
                            + "execution, or equity data."
            );
        }
        if (!portfolioStateAfter.equals(portfolioStateBefore)) {
            throw new IllegalArgumentException(
                    "NO_NEXT_DAILY_BAR result must preserve portfolio state."
            );
        }
    }

    private static void validateEquitySnapshot(
            LocalDate decisionDate,
            BacktestPortfolioState portfolioStateAfter,
            BacktestEquitySnapshot equitySnapshot
    ) {
        Objects.requireNonNull(
                equitySnapshot,
                "equitySnapshot must not be null."
        );
        if (!equitySnapshot.valuationDate().equals(decisionDate)) {
            throw new IllegalArgumentException(
                    "equitySnapshot valuationDate must match decisionDate."
            );
        }
        if (equitySnapshot.cashAmountKrw()
                != portfolioStateAfter.cashAmountKrw()) {
            throw new IllegalArgumentException(
                    "equitySnapshot cash must match portfolioStateAfter."
            );
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
        if (fill != null || portfolioTransition != null) {
            throw new IllegalArgumentException(
                    "HOLD result must not contain execution data."
            );
        }
        if (!portfolioStateAfter.equals(portfolioStateBefore)) {
            throw new IllegalArgumentException(
                    "HOLD result must preserve portfolio state."
            );
        }
    }

    private static void validateTransition(
            LocalDate signalDate,
            LocalDate decisionDate,
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
        if (!fill.signalDate().equals(signalDate)
                || !fill.fillDate().equals(decisionDate)) {
            throw new IllegalArgumentException(
                    "fill dates must match step dates."
            );
        }
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
                != decision.quantity()
                || fill.tradeCostCalculation().referencePriceKrw()
                != decision.expectedPriceKrw()) {
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
