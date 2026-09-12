package com.stock.market.price;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentPriceServiceTest {
    private final CurrentPriceService service = new CurrentPriceService();

    @Test
    void returnsFixedCurrentPriceForRequestedSymbol() {
        CurrentPriceSnapshot snapshot = service.getCurrentPrice("005930");

        assertThat(snapshot.symbol()).isEqualTo("005930");
        assertThat(snapshot.priceKrw()).isEqualTo(100_000L);
    }
}
