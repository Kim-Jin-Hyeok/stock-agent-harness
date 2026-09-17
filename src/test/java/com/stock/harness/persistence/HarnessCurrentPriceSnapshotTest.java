package com.stock.harness.persistence;

import com.stock.market.price.CurrentPriceSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessCurrentPriceSnapshotTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void fromCopiesCurrentPriceValues() {
        HarnessCurrentPriceSnapshot snapshot = HarnessCurrentPriceSnapshot.from(
                new CurrentPriceSnapshot("005930", 70_000L, OBSERVED_AT)
        );

        assertThat(snapshot.symbol()).isEqualTo("005930");
        assertThat(snapshot.priceKrw()).isEqualTo(70_000L);
        assertThat(snapshot.observedAt()).isEqualTo(OBSERVED_AT);
    }
}
