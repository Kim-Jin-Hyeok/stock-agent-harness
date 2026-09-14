package com.stock.market.price.lookup;

import com.stock.market.price.CurrentPriceSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentPriceLookupResultTest {

    @Test
    void createsCacheLookupResult() {
        CurrentPriceSnapshot snapshot = new CurrentPriceSnapshot("005930", 70_000L);

        CurrentPriceLookupResult result = CurrentPriceLookupResult.cache(snapshot);

        assertThat(result.snapshot()).isEqualTo(snapshot);
        assertThat(result.source()).isEqualTo(CurrentPriceLookupSource.CACHE);
    }

    @Test
    void createsProviderLookupResult() {
        CurrentPriceSnapshot snapshot = new CurrentPriceSnapshot("005930", 70_000L);

        CurrentPriceLookupResult result = CurrentPriceLookupResult.provider(snapshot);

        assertThat(result.snapshot()).isEqualTo(snapshot);
        assertThat(result.source()).isEqualTo(CurrentPriceLookupSource.PROVIDER);
    }
}
