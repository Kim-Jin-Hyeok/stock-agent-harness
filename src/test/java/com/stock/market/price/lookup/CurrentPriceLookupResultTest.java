package com.stock.market.price.lookup;

import com.stock.market.price.CurrentPriceSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentPriceLookupResultTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void createsCacheLookupResult() {
        CurrentPriceSnapshot snapshot = new CurrentPriceSnapshot("005930", 70_000L, OBSERVED_AT);

        CurrentPriceLookupResult result = CurrentPriceLookupResult.cache(snapshot);

        assertThat(result.snapshot()).isEqualTo(snapshot);
        assertThat(result.source()).isEqualTo(CurrentPriceLookupSource.CACHE);
    }

    @Test
    void createsProviderLookupResult() {
        CurrentPriceSnapshot snapshot = new CurrentPriceSnapshot("005930", 70_000L, OBSERVED_AT);

        CurrentPriceLookupResult result = CurrentPriceLookupResult.provider(snapshot);

        assertThat(result.snapshot()).isEqualTo(snapshot);
        assertThat(result.source()).isEqualTo(CurrentPriceLookupSource.PROVIDER);
    }
}
