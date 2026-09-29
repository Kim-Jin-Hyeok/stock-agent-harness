package com.stock.portfolio.valuation;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record PortfolioValuationSnapshot(
        Instant evaluatedAt,
        long cashAmountKrw,
        long positionEvaluationAmountKrw,
        long totalAssetAmountKrw,
        List<PortfolioPositionValuation> positions
) {
    public PortfolioValuationSnapshot {
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null.");
        if (cashAmountKrw < 0) {
            throw new IllegalArgumentException(
                    "cashAmountKrw must not be negative."
            );
        }
        if (positionEvaluationAmountKrw < 0) {
            throw new IllegalArgumentException(
                    "positionEvaluationAmountKrw must not be negative."
            );
        }
        if (totalAssetAmountKrw < 0) {
            throw new IllegalArgumentException(
                    "totalAssetAmountKrw must not be negative."
            );
        }

        positions = List.copyOf(Objects.requireNonNull(
                positions,
                "positions must not be null."
        ));
        validatePositions(positions, evaluatedAt);

        long calculatedPositionAmount = positions.stream()
                .mapToLong(
                        PortfolioPositionValuation::evaluationAmountKrw
                )
                .reduce(0L, Math::addExact);
        if (positionEvaluationAmountKrw != calculatedPositionAmount) {
            throw new IllegalArgumentException(
                    "positionEvaluationAmountKrw must match positions."
            );
        }
        if (totalAssetAmountKrw != Math.addExact(
                cashAmountKrw,
                positionEvaluationAmountKrw
        )) {
            throw new IllegalArgumentException(
                    "totalAssetAmountKrw must equal cash and position "
                            + "evaluation amount."
            );
        }
    }

    private static void validatePositions(
            List<PortfolioPositionValuation> positions,
            Instant evaluatedAt
    ) {
        Set<String> symbols = new HashSet<>();
        for (PortfolioPositionValuation position : positions) {
            Objects.requireNonNull(position, "position must not be null.");
            if (!symbols.add(position.symbol())) {
                throw new IllegalArgumentException(
                        "positions must not contain duplicate symbol. symbol="
                                + position.symbol()
                );
            }
            if (position.priceObservedAt().isAfter(evaluatedAt)) {
                throw new IllegalArgumentException(
                        "priceObservedAt must not be after evaluatedAt. "
                                + "symbol="
                                + position.symbol()
                );
            }
        }
    }
}
