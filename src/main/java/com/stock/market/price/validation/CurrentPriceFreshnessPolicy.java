package com.stock.market.price.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class CurrentPriceFreshnessPolicy {
    private final CurrentPriceFreshnessProperties properties;
    private final Clock clock;

    public boolean isFresh(Instant observedAt) {
        return isFreshAt(observedAt, clock.instant());
    }

    public boolean isFreshAt(Instant observedAt, Instant evaluatedAt) {
        if (observedAt == null) {
            return false;
        }

        Instant expiresAt = observedAt.plus(properties.maxAge());
        return evaluatedAt.isBefore(expiresAt);
    }
}
