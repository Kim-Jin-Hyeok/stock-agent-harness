package com.stock.strategy.profile;

import java.util.Objects;

public record InvestmentStrategyIdentity(
        String strategyId,
        int strategyVersion,
        InvestmentHorizon horizon
) {
    public InvestmentStrategyIdentity {
        if (strategyId == null || strategyId.isBlank()) {
            throw new IllegalArgumentException("strategyId must not be blank.");
        }
        if (strategyVersion < 1) {
            throw new IllegalArgumentException(
                    "strategyVersion must be at least 1."
            );
        }
        Objects.requireNonNull(horizon, "horizon must not be null.");
    }
}
