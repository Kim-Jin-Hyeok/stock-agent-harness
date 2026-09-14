package com.stock.market.price;

import com.stock.market.price.provider.CurrentPriceProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurrentPriceServiceTest {

    @Test
    void returnsCurrentPriceFromProvider() {
        CurrentPriceProvider provider = mock(CurrentPriceProvider.class);
        CurrentPriceSnapshot expected = new CurrentPriceSnapshot("005930", 70_000L);
        when(provider.getCurrentPrice("005930")).thenReturn(expected);
        CurrentPriceService service = new CurrentPriceService(provider);

        CurrentPriceSnapshot snapshot = service.getCurrentPrice("005930");

        assertThat(snapshot).isEqualTo(expected);
        verify(provider).getCurrentPrice("005930");
    }

    @Test
    void propagatesProviderException() {
        CurrentPriceProvider provider = mock(CurrentPriceProvider.class);
        when(provider.getCurrentPrice("005930"))
                .thenThrow(new IllegalStateException("Broker timeout"));
        CurrentPriceService service = new CurrentPriceService(provider);

        assertThatThrownBy(() -> service.getCurrentPrice("005930"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Broker timeout");
    }
}
