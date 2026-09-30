package com.stock.backtest.strategy.swing.v1.report.trade;

import com.stock.agent.InvestmentAction;
import com.stock.backtest.execution.fill.daily.DailyOpenFillApproximation;
import com.stock.trade.cost.model.TradeCostCalculation;

import java.util.Objects;

public record SwingV1CompletedTrade(
        String symbol,
        long quantity,
        DailyOpenFillApproximation entryFill,
        DailyOpenFillApproximation exitFill,
        long grossProfitLossAmountKrw,
        long totalCostAmountKrw,
        long netProfitLossAmountKrw
) {
    public SwingV1CompletedTrade {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive.");
        }
        Objects.requireNonNull(entryFill, "entryFill must not be null.");
        Objects.requireNonNull(exitFill, "exitFill must not be null.");

        TradeCostCalculation entryCalculation =
                entryFill.tradeCostCalculation();
        TradeCostCalculation exitCalculation =
                exitFill.tradeCostCalculation();
        validateFills(
                symbol,
                quantity,
                entryFill,
                exitFill,
                entryCalculation,
                exitCalculation
        );

        long expectedGrossProfitLossAmountKrw = Math.subtractExact(
                exitCalculation.referenceOrderAmountKrw(),
                entryCalculation.referenceOrderAmountKrw()
        );
        if (grossProfitLossAmountKrw
                != expectedGrossProfitLossAmountKrw) {
            throw new IllegalArgumentException(
                    "grossProfitLossAmountKrw must match fill reference "
                            + "amounts."
            );
        }
        long expectedTotalCostAmountKrw = Math.addExact(
                entryCalculation.totalCostAmountKrw(),
                exitCalculation.totalCostAmountKrw()
        );
        if (totalCostAmountKrw != expectedTotalCostAmountKrw) {
            throw new IllegalArgumentException(
                    "totalCostAmountKrw must match entry and exit costs."
            );
        }
        long expectedNetProfitLossAmountKrw = Math.subtractExact(
                exitCalculation.settlementAmountKrw(),
                entryCalculation.settlementAmountKrw()
        );
        if (netProfitLossAmountKrw
                != expectedNetProfitLossAmountKrw
                || netProfitLossAmountKrw != Math.subtractExact(
                        grossProfitLossAmountKrw,
                        totalCostAmountKrw
                )) {
            throw new IllegalArgumentException(
                    "netProfitLossAmountKrw must match settlement amounts "
                            + "and cost-adjusted gross profit."
            );
        }
    }

    public static SwingV1CompletedTrade from(
            DailyOpenFillApproximation entryFill,
            DailyOpenFillApproximation exitFill
    ) {
        Objects.requireNonNull(entryFill, "entryFill must not be null.");
        Objects.requireNonNull(exitFill, "exitFill must not be null.");
        TradeCostCalculation entryCalculation =
                entryFill.tradeCostCalculation();
        TradeCostCalculation exitCalculation =
                exitFill.tradeCostCalculation();
        long grossProfitLossAmountKrw = Math.subtractExact(
                exitCalculation.referenceOrderAmountKrw(),
                entryCalculation.referenceOrderAmountKrw()
        );
        long totalCostAmountKrw = Math.addExact(
                entryCalculation.totalCostAmountKrw(),
                exitCalculation.totalCostAmountKrw()
        );
        long netProfitLossAmountKrw = Math.subtractExact(
                exitCalculation.settlementAmountKrw(),
                entryCalculation.settlementAmountKrw()
        );
        return new SwingV1CompletedTrade(
                entryFill.symbol(),
                entryCalculation.quantity(),
                entryFill,
                exitFill,
                grossProfitLossAmountKrw,
                totalCostAmountKrw,
                netProfitLossAmountKrw
        );
    }

    private static void validateFills(
            String symbol,
            long quantity,
            DailyOpenFillApproximation entryFill,
            DailyOpenFillApproximation exitFill,
            TradeCostCalculation entryCalculation,
            TradeCostCalculation exitCalculation
    ) {
        if (entryCalculation.action() != InvestmentAction.BUY) {
            throw new IllegalArgumentException(
                    "entryFill must contain BUY action."
            );
        }
        if (exitCalculation.action() != InvestmentAction.SELL) {
            throw new IllegalArgumentException(
                    "exitFill must contain SELL action."
            );
        }
        if (!symbol.equals(entryFill.symbol())
                || !symbol.equals(exitFill.symbol())) {
            throw new IllegalArgumentException(
                    "entryFill and exitFill symbols must match trade symbol."
            );
        }
        if (quantity != entryCalculation.quantity()
                || quantity != exitCalculation.quantity()) {
            throw new IllegalArgumentException(
                    "entryFill and exitFill quantities must match trade "
                            + "quantity."
            );
        }
        if (!entryCalculation.costModel().equals(
                exitCalculation.costModel()
        )) {
            throw new IllegalArgumentException(
                    "entryFill and exitFill cost models must match."
            );
        }
        if (!exitFill.fillDate().isAfter(entryFill.fillDate())) {
            throw new IllegalArgumentException(
                    "exitFill fillDate must be after entryFill fillDate."
            );
        }
    }
}
