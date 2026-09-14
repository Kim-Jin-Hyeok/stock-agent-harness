package com.stock.market.price.provider;

import com.stock.market.price.CurrentPriceSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixedCurrentPriceProviderTest {
    private final FixedCurrentPriceProvider provider = new FixedCurrentPriceProvider();

    @Test
    void returnsFixedCurrentPriceForRequestedSymbol() {
        CurrentPriceSnapshot snapshot = provider.getCurrentPrice("005930");

        assertThat(snapshot.symbol()).isEqualTo("005930");
        assertThat(snapshot.priceKrw()).isEqualTo(100_000L);
    }
}
