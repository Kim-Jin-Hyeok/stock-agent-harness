package com.stock.backtest.portfolio.transition;

import com.stock.agent.InvestmentAction;
import com.stock.backtest.execution.fill.daily.DailyOpenFillApproximation;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.BacktestPosition;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionReasonCode;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionResult;
import com.stock.trade.cost.model.TradeCostCalculation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class BacktestPortfolioTransitionService {

    public BacktestPortfolioTransitionResult apply(
            BacktestPortfolioState currentState,
            DailyOpenFillApproximation fill
    ) {
        Objects.requireNonNull(
                currentState,
                "currentState must not be null."
        );
        Objects.requireNonNull(fill, "fill must not be null.");

        return switch (fill.tradeCostCalculation().action()) {
            case BUY -> applyBuy(currentState, fill);
            case SELL -> applySell(currentState, fill);
            case HOLD -> throw new IllegalStateException(
                    "Daily open fill must not contain HOLD action."
            );
        };
    }

    private BacktestPortfolioTransitionResult applyBuy(
            BacktestPortfolioState currentState,
            DailyOpenFillApproximation fill
    ) {
        TradeCostCalculation calculation = fill.tradeCostCalculation();
        long requiredCashAmountKrw = calculation.settlementAmountKrw();
        if (currentState.cashAmountKrw() < requiredCashAmountKrw) {
            return BacktestPortfolioTransitionResult.rejected(
                    BacktestPortfolioTransitionReasonCode.INSUFFICIENT_CASH,
                    "Backtest portfolio cash is insufficient. required="
                            + requiredCashAmountKrw
                            + ", available="
                            + currentState.cashAmountKrw(),
                    currentState
            );
        }

        List<BacktestPosition> updatedPositions = new ArrayList<>();
        boolean merged = false;
        for (BacktestPosition position : currentState.positions()) {
            if (fill.symbol().equals(position.symbol())) {
                updatedPositions.add(mergeBuyPosition(
                        position,
                        calculation
                ));
                merged = true;
            } else {
                updatedPositions.add(position);
            }
        }
        if (!merged) {
            updatedPositions.add(new BacktestPosition(
                    fill.symbol(),
                    calculation.quantity(),
                    calculation.executionPriceKrw()
            ));
        }

        BacktestPortfolioState updatedState = new BacktestPortfolioState(
                Math.subtractExact(
                        currentState.cashAmountKrw(),
                        requiredCashAmountKrw
                ),
                updatedPositions
        );
        return BacktestPortfolioTransitionResult.applied(updatedState);
    }

    private BacktestPosition mergeBuyPosition(
            BacktestPosition position,
            TradeCostCalculation calculation
    ) {
        long mergedQuantity = Math.addExact(
                position.quantity(),
                calculation.quantity()
        );
        long currentExecutionAmountKrw = Math.multiplyExact(
                position.quantity(),
                position.averageExecutionPriceKrw()
        );
        long mergedExecutionAmountKrw = Math.addExact(
                currentExecutionAmountKrw,
                calculation.executionOrderAmountKrw()
        );
        return new BacktestPosition(
                position.symbol(),
                mergedQuantity,
                mergedExecutionAmountKrw / mergedQuantity
        );
    }

    private BacktestPortfolioTransitionResult applySell(
            BacktestPortfolioState currentState,
            DailyOpenFillApproximation fill
    ) {
        BacktestPosition position = currentState
                .findPosition(fill.symbol())
                .orElse(null);
        if (position == null) {
            return BacktestPortfolioTransitionResult.rejected(
                    BacktestPortfolioTransitionReasonCode.POSITION_NOT_FOUND,
                    "Backtest portfolio position was not found. symbol="
                            + fill.symbol(),
                    currentState
            );
        }

        long sellQuantity = fill.tradeCostCalculation().quantity();
        if (position.quantity() < sellQuantity) {
            return BacktestPortfolioTransitionResult.rejected(
                    BacktestPortfolioTransitionReasonCode
                            .INSUFFICIENT_POSITION_QUANTITY,
                    "Backtest portfolio quantity is insufficient. requested="
                            + sellQuantity
                            + ", available="
                            + position.quantity(),
                    currentState
            );
        }

        long remainingQuantity = Math.subtractExact(
                position.quantity(),
                sellQuantity
        );
        List<BacktestPosition> updatedPositions = new ArrayList<>();
        for (BacktestPosition currentPosition : currentState.positions()) {
            if (!fill.symbol().equals(currentPosition.symbol())) {
                updatedPositions.add(currentPosition);
                continue;
            }
            if (remainingQuantity > 0) {
                updatedPositions.add(new BacktestPosition(
                        position.symbol(),
                        remainingQuantity,
                        position.averageExecutionPriceKrw()
                ));
            }
        }

        BacktestPortfolioState updatedState = new BacktestPortfolioState(
                Math.addExact(
                        currentState.cashAmountKrw(),
                        fill.tradeCostCalculation().settlementAmountKrw()
                ),
                updatedPositions
        );
        return BacktestPortfolioTransitionResult.applied(updatedState);
    }
}
