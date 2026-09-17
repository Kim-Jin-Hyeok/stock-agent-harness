package com.stock.market.price.provider;

import com.stock.market.price.CurrentPriceSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FixedCurrentPriceProviderTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private final FixedCurrentPriceProvider provider = new FixedCurrentPriceProvider(
            Clock.fixed(OBSERVED_AT, ZoneOffset.UTC)
    );

    @Test
    void returnsFixedCurrentPriceForRequestedSymbol() {
        CurrentPriceSnapshot snapshot = provider.getCurrentPrice("005930");

        assertThat(snapshot.symbol()).isEqualTo("005930");
        assertThat(snapshot.priceKrw()).isEqualTo(100_000L);
        assertThat(snapshot.observedAt()).isEqualTo(OBSERVED_AT);
    }
}
