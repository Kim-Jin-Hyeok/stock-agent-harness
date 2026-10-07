package com.stock.strategy.universe.eligibility.restriction.kis.freshness.request;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record KisStockRestrictionFreshnessRequest(
        Instant evaluatedAt,
        Duration maxMasterAge,
        Duration maxBasicInfoAge
) {
    public KisStockRestrictionFreshnessRequest {
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null.");
        requirePositive(maxMasterAge, "maxMasterAge");
        requirePositive(maxBasicInfoAge, "maxBasicInfoAge");
    }

    private static void requirePositive(Duration age, String name) {
        Objects.requireNonNull(age, name + " must not be null.");
        if (age.isZero() || age.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
    }
}
