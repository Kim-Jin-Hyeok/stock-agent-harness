package com.stock.risk.capacity;

import com.stock.agent.InvestmentAction;

import java.math.BigDecimal;
import java.util.Objects;

public record OrderQuantityCapacity(
        InvestmentAction action,
        String symbol,
        long currentPriceKrw,
        long currentPositionQuantity,
        long maxAffordableQuantity,
        long maxOrderRatioQuantity,
        long maxPositionRatioQuantity,
        long maxAllowedQuantity,
        BigDecimal oneSharePortfolioRatio
) {
    public OrderQuantityCapacity {
        Objects.requireNonNull(action, "action must not be null.");
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        if (currentPriceKrw <= 0) {
            throw new IllegalArgumentException(
                    "currentPriceKrw must be positive."
            );
        }
        if (currentPositionQuantity < 0
                || maxAffordableQuantity < 0
                || maxOrderRatioQuantity < 0
                || maxPositionRatioQuantity < 0
                || maxAllowedQuantity < 0) {
            throw new IllegalArgumentException(
                    "Order quantity capacities must not be negative."
            );
        }
        Objects.requireNonNull(
                oneSharePortfolioRatio,
                "oneSharePortfolioRatio must not be null."
        );
        if (oneSharePortfolioRatio.signum() < 0) {
            throw new IllegalArgumentException(
                    "oneSharePortfolioRatio must not be negative."
            );
        }

        switch (action) {
            case BUY -> validateBuyCapacity(
                    maxAffordableQuantity,
                    maxOrderRatioQuantity,
                    maxPositionRatioQuantity,
                    maxAllowedQuantity
            );
            case SELL -> {
                if (maxAllowedQuantity != currentPositionQuantity) {
                    throw new IllegalArgumentException(
                            "SELL maxAllowedQuantity must match current "
                                    + "position quantity."
                    );
                }
            }
            case HOLD -> {
                if (maxAllowedQuantity != 0) {
                    throw new IllegalArgumentException(
                            "HOLD maxAllowedQuantity must be zero."
                    );
                }
            }
        }
    }

    public boolean canOrder() {
        return maxAllowedQuantity > 0;
    }

    private static void validateBuyCapacity(
            long maxAffordableQuantity,
            long maxOrderRatioQuantity,
            long maxPositionRatioQuantity,
            long maxAllowedQuantity
    ) {
        long expectedMaxAllowedQuantity = Math.min(
                maxAffordableQuantity,
                Math.min(
                        maxOrderRatioQuantity,
                        maxPositionRatioQuantity
                )
        );
        if (maxAllowedQuantity != expectedMaxAllowedQuantity) {
            throw new IllegalArgumentException(
                    "BUY maxAllowedQuantity must be the minimum capacity."
            );
        }
    }
}
