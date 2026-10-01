package com.stock.backtest.comparison.buyandhold.model;

import com.stock.agent.InvestmentAction;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.performance.metric.BacktestPerformanceSummary;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.BacktestPosition;
import com.stock.trade.cost.model.TradeCostCalculation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;

public record BuyAndHoldBacktestResult(
        BuyAndHoldBacktestRequest request,
        TradeCostCalculation buyCostCalculation,
        BacktestPortfolioState finalPortfolioState,
        List<BacktestEquitySnapshot> equityCurve,
        BacktestPerformanceSummary performanceSummary,
        TradeCostCalculation terminalSellCostCalculation,
        long estimatedTerminalLiquidationCostAmountKrw,
        long liquidationAdjustedFinalEquityAmountKrw,
        long liquidationAdjustedNetProfitAmountKrw,
        BigDecimal liquidationAdjustedTotalReturnRate
) {
    public BuyAndHoldBacktestResult {
        Objects.requireNonNull(request, "request must not be null.");
        Objects.requireNonNull(
                finalPortfolioState,
                "finalPortfolioState must not be null."
        );
        equityCurve = List.copyOf(Objects.requireNonNull(
                equityCurve,
                "equityCurve must not be null."
        ));
        Objects.requireNonNull(
                performanceSummary,
                "performanceSummary must not be null."
        );
        validatePortfolio(request, buyCostCalculation, finalPortfolioState);
        validateEquityCurve(request, finalPortfolioState, equityCurve);
        if (performanceSummary.initialEquityAmountKrw()
                != request.initialCashAmountKrw()
                || performanceSummary.finalEquityAmountKrw()
                != equityCurve.getLast().totalAssetAmountKrw()
                || performanceSummary.observationCount() != equityCurve.size()) {
            throw new IllegalArgumentException(
                    "performanceSummary must match request and equityCurve."
            );
        }
        validateLiquidation(
                request,
                buyCostCalculation,
                terminalSellCostCalculation,
                equityCurve,
                estimatedTerminalLiquidationCostAmountKrw
        );
        long expectedFinalEquity = Math.subtractExact(
                performanceSummary.finalEquityAmountKrw(),
                estimatedTerminalLiquidationCostAmountKrw
        );
        long expectedNetProfit = Math.subtractExact(
                expectedFinalEquity,
                request.initialCashAmountKrw()
        );
        Objects.requireNonNull(
                liquidationAdjustedTotalReturnRate,
                "liquidationAdjustedTotalReturnRate must not be null."
        );
        BigDecimal expectedReturnRate = BigDecimal.valueOf(expectedNetProfit)
                .divide(
                        BigDecimal.valueOf(request.initialCashAmountKrw()),
                        MathContext.DECIMAL128
                );
        if (liquidationAdjustedFinalEquityAmountKrw != expectedFinalEquity
                || liquidationAdjustedNetProfitAmountKrw != expectedNetProfit
                || liquidationAdjustedTotalReturnRate.compareTo(
                        expectedReturnRate
                ) != 0) {
            throw new IllegalArgumentException(
                    "Liquidation-adjusted amounts and return must deduct "
                            + "terminal sell cost exactly once."
            );
        }
    }

    public long boughtQuantity() {
        return buyCostCalculation == null ? 0L : buyCostCalculation.quantity();
    }

    private static void validatePortfolio(
            BuyAndHoldBacktestRequest request,
            TradeCostCalculation buy,
            BacktestPortfolioState portfolio
    ) {
        if (buy == null) {
            if (!portfolio.equals(BacktestPortfolioState.withCash(
                    request.initialCashAmountKrw()
            ))) {
                throw new IllegalArgumentException(
                        "Without a purchase the initial cash must be preserved."
                );
            }
            return;
        }
        if (buy.action() != InvestmentAction.BUY
                || !buy.costModel().equals(request.costModel())
                || buy.settlementAmountKrw() > request.buyBudgetAmountKrw()) {
            throw new IllegalArgumentException(
                    "Purchase must use request costModel and fit buy budget."
            );
        }
        BacktestPortfolioState expected = new BacktestPortfolioState(
                Math.subtractExact(
                        request.initialCashAmountKrw(),
                        buy.settlementAmountKrw()
                ),
                List.of(new BacktestPosition(
                        request.symbol(),
                        buy.quantity(),
                        buy.executionPriceKrw()
                ))
        );
        if (!portfolio.equals(expected)) {
            throw new IllegalArgumentException(
                    "finalPortfolioState must preserve the single purchase."
            );
        }
    }

    private static void validateEquityCurve(
            BuyAndHoldBacktestRequest request,
            BacktestPortfolioState portfolio,
            List<BacktestEquitySnapshot> curve
    ) {
        if (curve.size() != request.valuationInstants().size()) {
            throw new IllegalArgumentException(
                    "equityCurve must match requested valuation instants."
            );
        }
        for (int index = 0; index < curve.size(); index++) {
            BacktestEquitySnapshot snapshot = curve.get(index);
            if (!snapshot.evaluatedAt().equals(
                    request.valuationInstants().get(index)
            ) || snapshot.cashAmountKrw() != portfolio.cashAmountKrw()) {
                throw new IllegalArgumentException(
                        "equityCurve must preserve valuation instants and cash."
                );
            }
            if (portfolio.positions().isEmpty()) {
                if (snapshot.positionEvaluationAmountKrw() != 0) {
                    throw new IllegalArgumentException(
                            "A cash-only comparison must have zero position value."
                    );
                }
            } else {
                long quantity = portfolio.positions().getFirst().quantity();
                if (snapshot.positionEvaluationAmountKrw() <= 0
                        || snapshot.positionEvaluationAmountKrw() % quantity != 0) {
                    throw new IllegalArgumentException(
                            "Position value must resolve to an exact positive price."
                    );
                }
            }
        }
    }

    private static void validateLiquidation(
            BuyAndHoldBacktestRequest request,
            TradeCostCalculation buy,
            TradeCostCalculation sell,
            List<BacktestEquitySnapshot> curve,
            long terminalCost
    ) {
        if (buy == null) {
            if (sell != null || terminalCost != 0) {
                throw new IllegalArgumentException(
                        "Without a purchase there must be no terminal sell cost."
                );
            }
            return;
        }
        Objects.requireNonNull(sell, "terminalSellCostCalculation must not be null.");
        if (curve.getFirst().positionEvaluationAmountKrw()
                != buy.referenceOrderAmountKrw()
                || sell.action() != InvestmentAction.SELL
                || !sell.costModel().equals(request.costModel())
                || sell.quantity() != buy.quantity()
                || sell.referenceOrderAmountKrw()
                != curve.getLast().positionEvaluationAmountKrw()
                || terminalCost != sell.totalCostAmountKrw()) {
            throw new IllegalArgumentException(
                    "Buy and terminal sell must match first and last valuations."
            );
        }
    }
}
