package com.stock.market.price.observation;

import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

import java.time.Instant;
import java.util.Objects;

public record CurrentPriceObservation(
        Long id,
        String runId,
        InvestmentStrategyIdentity strategyIdentity,
        String symbol,
        long priceKrw,
        Instant observedAt,
        CurrentPriceLookupSource source
) {
    public CurrentPriceObservation {
        if (id != null && id < 1) {
            throw new IllegalArgumentException(
                    "id must be positive when present."
            );
        }
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank.");
        }
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        if (priceKrw <= 0) {
            throw new IllegalArgumentException("priceKrw must be positive.");
        }
        Objects.requireNonNull(observedAt, "observedAt must not be null.");
        Objects.requireNonNull(source, "source must not be null.");
    }
}
