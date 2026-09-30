package com.stock.backtest.strategy.swing.v1.report.terminal;

import com.stock.agent.InvestmentAction;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.BacktestPosition;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostCalculation;
import com.stock.trade.cost.model.TradeCostModel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

@Component
public class SwingV1TerminalLiquidationCalculator {
    private static final MathContext RATE_MATH_CONTEXT =
            MathContext.DECIMAL128;

    private final TradeCostCalculator tradeCostCalculator;

    public SwingV1TerminalLiquidationCalculator(
            TradeCostCalculator tradeCostCalculator
    ) {
        this.tradeCostCalculator = Objects.requireNonNull(
                tradeCostCalculator,
                "tradeCostCalculator must not be null."
        );
    }

    public SwingV1TerminalLiquidationEstimate calculate(
            long initialEquityAmountKrw,
            BacktestPortfolioState finalPortfolioState,
            BacktestEquitySnapshot terminalSnapshot,
            TradeCostModel costModel
    ) {
        if (initialEquityAmountKrw <= 0) {
            throw new IllegalArgumentException(
                    "initialEquityAmountKrw must be positive."
            );
        }
        Objects.requireNonNull(
                finalPortfolioState,
                "finalPortfolioState must not be null."
        );
        Objects.requireNonNull(
                terminalSnapshot,
                "terminalSnapshot must not be null."
        );
        Objects.requireNonNull(costModel, "costModel must not be null.");
        validateTerminalState(finalPortfolioState, terminalSnapshot);

        long estimatedLiquidationCostAmountKrw =
                estimatedLiquidationCostAmountKrw(
                        finalPortfolioState,
                        terminalSnapshot,
                        costModel
                );
        long markToMarketFinalEquityAmountKrw =
                terminalSnapshot.totalAssetAmountKrw();
        long liquidationAdjustedFinalEquityAmountKrw = Math.subtractExact(
                markToMarketFinalEquityAmountKrw,
                estimatedLiquidationCostAmountKrw
        );
        long liquidationAdjustedNetProfitAmountKrw = Math.subtractExact(
                liquidationAdjustedFinalEquityAmountKrw,
                initialEquityAmountKrw
        );
        BigDecimal liquidationAdjustedTotalReturnRate = BigDecimal
                .valueOf(liquidationAdjustedNetProfitAmountKrw)
                .divide(
                        BigDecimal.valueOf(initialEquityAmountKrw),
                        RATE_MATH_CONTEXT
                );
        return new SwingV1TerminalLiquidationEstimate(
                initialEquityAmountKrw,
                markToMarketFinalEquityAmountKrw,
                estimatedLiquidationCostAmountKrw,
                liquidationAdjustedFinalEquityAmountKrw,
                liquidationAdjustedNetProfitAmountKrw,
                liquidationAdjustedTotalReturnRate
        );
    }

    private void validateTerminalState(
            BacktestPortfolioState finalPortfolioState,
            BacktestEquitySnapshot terminalSnapshot
    ) {
        if (finalPortfolioState.cashAmountKrw()
                != terminalSnapshot.cashAmountKrw()) {
            throw new IllegalArgumentException(
                    "terminalSnapshot cash must match finalPortfolioState."
            );
        }
        if (finalPortfolioState.positions().size() > 1) {
            throw new IllegalArgumentException(
                    "finalPortfolioState must contain at most one position "
                            + "for SWING_V1 terminal liquidation."
            );
        }
        if (finalPortfolioState.positions().isEmpty()
                && terminalSnapshot.positionEvaluationAmountKrw() != 0) {
            throw new IllegalArgumentException(
                    "terminalSnapshot position evaluation must be zero "
                            + "without a final position."
            );
        }
    }

    private long estimatedLiquidationCostAmountKrw(
            BacktestPortfolioState finalPortfolioState,
            BacktestEquitySnapshot terminalSnapshot,
            TradeCostModel costModel
    ) {
        if (finalPortfolioState.positions().isEmpty()) {
            return 0L;
        }

        BacktestPosition position = finalPortfolioState.positions()
                .getFirst();
        long positionEvaluationAmountKrw =
                terminalSnapshot.positionEvaluationAmountKrw();
        if (positionEvaluationAmountKrw <= 0
                || positionEvaluationAmountKrw % position.quantity() != 0) {
            throw new IllegalArgumentException(
                    "terminalSnapshot position evaluation must resolve to "
                            + "an exact positive price for the final "
                            + "position."
            );
        }
        long referencePriceKrw = positionEvaluationAmountKrw
                / position.quantity();
        TradeCostCalculation calculation = tradeCostCalculator.calculate(
                costModel,
                InvestmentAction.SELL,
                position.quantity(),
                referencePriceKrw
        );
        return calculation.totalCostAmountKrw();
    }
}
