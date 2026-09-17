package com.stock.market.price.validation;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentPriceFreshnessPolicyTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:01:00Z");
    private static final Duration MAX_AGE = Duration.ofMinutes(1);

    @Test
    void returnsTrueBeforeMaxAgeBoundary() {
        CurrentPriceFreshnessPolicy policy = policy();

        assertThat(policy.isFresh(NOW.minus(MAX_AGE).plusNanos(1))).isTrue();
    }

    @Test
    void returnsFalseAtMaxAgeBoundary() {
        CurrentPriceFreshnessPolicy policy = policy();

        assertThat(policy.isFresh(NOW.minus(MAX_AGE))).isFalse();
    }

    @Test
    void returnsFalseWhenObservedAtIsMissing() {
        assertThat(policy().isFresh(null)).isFalse();
    }

    @Test
    void rejectsZeroMaxAge() {
        assertThatThrownBy(() -> new CurrentPriceFreshnessProperties(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Current price max age must be positive.");
    }

    @Test
    void rejectsNegativeMaxAge() {
        assertThatThrownBy(() -> new CurrentPriceFreshnessProperties(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Current price max age must be positive.");
    }

    @Test
    void rejectsMissingMaxAge() {
        assertThatThrownBy(() -> new CurrentPriceFreshnessProperties(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Current price max age must be positive.");
    }

    private CurrentPriceFreshnessPolicy policy() {
        return new CurrentPriceFreshnessPolicy(
                new CurrentPriceFreshnessProperties(MAX_AGE),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
