package com.stock.trade.cost.model;

import com.stock.agent.InvestmentAction;

import java.util.Objects;

public record TradeCostCalculation(
        TradeCostModel costModel,
        InvestmentAction action,
        long quantity,
        long referencePriceKrw,
        long executionPriceKrw,
        long referenceOrderAmountKrw,
        long executionOrderAmountKrw,
        long commissionAmountKrw,
        long taxAmountKrw,
        long slippageAmountKrw,
        long totalCostAmountKrw,
        long settlementAmountKrw
) {
    public TradeCostCalculation {
        Objects.requireNonNull(costModel, "costModel must not be null.");
        Objects.requireNonNull(action, "action must not be null.");
        if (action == InvestmentAction.HOLD) {
            throw new IllegalArgumentException(
                    "Trade cost calculation requires BUY or SELL action."
            );
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive."
            );
        }
        if (referencePriceKrw <= 0) {
            throw new IllegalArgumentException(
                    "referencePriceKrw must be positive."
            );
        }
        if (executionPriceKrw <= 0) {
            throw new IllegalArgumentException(
                    "executionPriceKrw must be positive."
            );
        }
        if (commissionAmountKrw < 0
                || taxAmountKrw < 0
                || slippageAmountKrw < 0
                || totalCostAmountKrw < 0) {
            throw new IllegalArgumentException(
                    "Trade cost amounts must not be negative."
            );
        }
        if (action == InvestmentAction.BUY && taxAmountKrw != 0) {
            throw new IllegalArgumentException(
                    "BUY taxAmountKrw must be zero."
            );
        }
        if (settlementAmountKrw <= 0) {
            throw new IllegalArgumentException(
                    "settlementAmountKrw must be positive."
            );
        }

        validateAmounts(
                action,
                quantity,
                referencePriceKrw,
                executionPriceKrw,
                referenceOrderAmountKrw,
                executionOrderAmountKrw,
                commissionAmountKrw,
                taxAmountKrw,
                slippageAmountKrw,
                totalCostAmountKrw,
                settlementAmountKrw
        );
    }

    private static void validateAmounts(
            InvestmentAction action,
            long quantity,
            long referencePriceKrw,
            long executionPriceKrw,
            long referenceOrderAmountKrw,
            long executionOrderAmountKrw,
            long commissionAmountKrw,
            long taxAmountKrw,
            long slippageAmountKrw,
            long totalCostAmountKrw,
            long settlementAmountKrw
    ) {
        long expectedReferenceAmount = Math.multiplyExact(
                quantity,
                referencePriceKrw
        );
        long expectedExecutionAmount = Math.multiplyExact(
                quantity,
                executionPriceKrw
        );
        if (referenceOrderAmountKrw != expectedReferenceAmount) {
            throw new IllegalArgumentException(
                    "referenceOrderAmountKrw must match quantity and "
                            + "referencePriceKrw."
            );
        }
        if (executionOrderAmountKrw != expectedExecutionAmount) {
            throw new IllegalArgumentException(
                    "executionOrderAmountKrw must match quantity and "
                            + "executionPriceKrw."
            );
        }

        long expectedSlippageAmount = switch (action) {
            case BUY -> {
                if (executionPriceKrw < referencePriceKrw) {
                    throw new IllegalArgumentException(
                            "BUY executionPriceKrw must not be lower than "
                                    + "referencePriceKrw."
                    );
                }
                yield Math.subtractExact(
                        executionOrderAmountKrw,
                        referenceOrderAmountKrw
                );
            }
            case SELL -> {
                if (executionPriceKrw > referencePriceKrw) {
                    throw new IllegalArgumentException(
                            "SELL executionPriceKrw must not be higher than "
                                    + "referencePriceKrw."
                    );
                }
                yield Math.subtractExact(
                        referenceOrderAmountKrw,
                        executionOrderAmountKrw
                );
            }
            case HOLD -> throw new IllegalStateException(
                    "HOLD action must already be rejected."
            );
        };
        if (slippageAmountKrw != expectedSlippageAmount) {
            throw new IllegalArgumentException(
                    "slippageAmountKrw must match the adverse price "
                            + "difference."
            );
        }

        long expectedTotalCost = Math.addExact(
                slippageAmountKrw,
                Math.addExact(commissionAmountKrw, taxAmountKrw)
        );
        if (totalCostAmountKrw != expectedTotalCost) {
            throw new IllegalArgumentException(
                    "totalCostAmountKrw must match all cost amounts."
            );
        }

        long expectedSettlementAmount = switch (action) {
            case BUY -> Math.addExact(
                    executionOrderAmountKrw,
                    Math.addExact(commissionAmountKrw, taxAmountKrw)
            );
            case SELL -> Math.subtractExact(
                    executionOrderAmountKrw,
                    Math.addExact(commissionAmountKrw, taxAmountKrw)
            );
            case HOLD -> throw new IllegalStateException(
                    "HOLD action must already be rejected."
            );
        };
        if (settlementAmountKrw != expectedSettlementAmount) {
            throw new IllegalArgumentException(
                    "settlementAmountKrw must match action settlement."
            );
        }
    }
}
