package com.stock.portfolio.valuation;

import java.time.Instant;
import java.util.Objects;

public record PortfolioPositionValuation(
        String symbol,
        long quantity,
        long averagePriceKrw,
        long currentPriceKrw,
        long acquisitionAmountKrw,
        long evaluationAmountKrw,
        long unrealizedProfitLossKrw,
        Instant priceObservedAt
) {
    public PortfolioPositionValuation {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive.");
        }
        if (averagePriceKrw <= 0) {
            throw new IllegalArgumentException(
                    "averagePriceKrw must be positive."
            );
        }
        if (currentPriceKrw <= 0) {
            throw new IllegalArgumentException(
                    "currentPriceKrw must be positive."
            );
        }
        if (acquisitionAmountKrw <= 0) {
            throw new IllegalArgumentException(
                    "acquisitionAmountKrw must be positive."
            );
        }
        if (evaluationAmountKrw <= 0) {
            throw new IllegalArgumentException(
                    "evaluationAmountKrw must be positive."
            );
        }
        if (unrealizedProfitLossKrw != Math.subtractExact(
                evaluationAmountKrw,
                acquisitionAmountKrw
        )) {
            throw new IllegalArgumentException(
                    "unrealizedProfitLossKrw must match evaluation amount "
                            + "minus acquisition amount."
            );
        }
        Objects.requireNonNull(
                priceObservedAt,
                "priceObservedAt must not be null."
        );
    }
}
