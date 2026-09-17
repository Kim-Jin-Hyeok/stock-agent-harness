package com.stock.market.price;

import com.stock.market.price.cache.CurrentPriceCache;
import com.stock.market.price.cache.CurrentPriceCacheProperties;
import com.stock.market.price.lookup.CurrentPriceLookupResult;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.market.price.provider.CurrentPriceProvider;
import com.stock.market.price.provider.CurrentPriceProviderCallGuard;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CurrentPriceServiceTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void returnsCachedCurrentPriceWithoutCallingProvider() {
        CurrentPriceProvider provider = mock(CurrentPriceProvider.class);
        CurrentPriceCache cache = mock(CurrentPriceCache.class);
        CurrentPriceSnapshot cached = new CurrentPriceSnapshot("005930", 70_000L, OBSERVED_AT);
        when(cache.get("005930")).thenReturn(Optional.of(cached));
        CurrentPriceService service = new CurrentPriceService(provider, cache);
        CurrentPriceProviderCallGuard providerCallGuard = mock(
                CurrentPriceProviderCallGuard.class
        );

        CurrentPriceLookupResult result = service.getCurrentPrice(
                "005930",
                providerCallGuard
        );

        assertThat(result.snapshot()).isEqualTo(cached);
        assertThat(result.source()).isEqualTo(CurrentPriceLookupSource.CACHE);
        verify(provider, never()).getCurrentPrice("005930");
        verify(cache, never()).put("005930", cached);
        verifyNoInteractions(providerCallGuard);
    }

    @Test
    void loadsAndCachesCurrentPriceOnCacheMiss() {
        CurrentPriceProvider provider = mock(CurrentPriceProvider.class);
        CurrentPriceCache cache = mock(CurrentPriceCache.class);
        CurrentPriceSnapshot loaded = new CurrentPriceSnapshot("005930", 70_000L, OBSERVED_AT);
        when(cache.get("005930")).thenReturn(Optional.empty());
        when(provider.getCurrentPrice("005930")).thenReturn(loaded);
        CurrentPriceService service = new CurrentPriceService(provider, cache);
        CurrentPriceProviderCallGuard providerCallGuard = mock(
                CurrentPriceProviderCallGuard.class
        );

        CurrentPriceLookupResult result = service.getCurrentPrice(
                "005930",
                providerCallGuard
        );

        assertThat(result.snapshot()).isEqualTo(loaded);
        assertThat(result.source()).isEqualTo(CurrentPriceLookupSource.PROVIDER);
        verify(provider).getCurrentPrice("005930");
        verify(cache).put("005930", loaded);
        verify(providerCallGuard).beforeCall();
    }

    @Test
    void callsProviderOnceForRepeatedRequestWithinTtl() {
        CurrentPriceProvider provider = mock(CurrentPriceProvider.class);
        CurrentPriceSnapshot loaded = new CurrentPriceSnapshot("005930", 70_000L, OBSERVED_AT);
        when(provider.getCurrentPrice("005930")).thenReturn(loaded);
        CurrentPriceCache cache = new CurrentPriceCache(
                new CurrentPriceCacheProperties(Duration.ofSeconds(30)),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
        );
        CurrentPriceService service = new CurrentPriceService(provider, cache);

        CurrentPriceLookupResult first = service.getCurrentPrice("005930", () -> {});
        CurrentPriceLookupResult second = service.getCurrentPrice("005930", () -> {});

        assertThat(first.snapshot()).isEqualTo(loaded);
        assertThat(first.source()).isEqualTo(CurrentPriceLookupSource.PROVIDER);
        assertThat(second.snapshot()).isEqualTo(loaded);
        assertThat(second.source()).isEqualTo(CurrentPriceLookupSource.CACHE);
        verify(provider, times(1)).getCurrentPrice("005930");
    }

    @Test
    void doesNotCacheNullProviderResult() {
        CurrentPriceProvider provider = mock(CurrentPriceProvider.class);
        CurrentPriceCache cache = mock(CurrentPriceCache.class);
        when(cache.get("005930")).thenReturn(Optional.empty());
        when(provider.getCurrentPrice("005930")).thenReturn(null);
        CurrentPriceService service = new CurrentPriceService(provider, cache);

        CurrentPriceLookupResult result = service.getCurrentPrice("005930", () -> {});

        assertThat(result.snapshot()).isNull();
        assertThat(result.source()).isEqualTo(CurrentPriceLookupSource.PROVIDER);
        verify(cache, never()).put(eq("005930"), any());
    }

    @Test
    void doesNotCacheProviderResultWithDifferentSymbol() {
        CurrentPriceProvider provider = mock(CurrentPriceProvider.class);
        CurrentPriceCache cache = mock(CurrentPriceCache.class);
        CurrentPriceSnapshot mismatched = new CurrentPriceSnapshot("000660", 120_000L, OBSERVED_AT);
        when(cache.get("005930")).thenReturn(Optional.empty());
        when(provider.getCurrentPrice("005930")).thenReturn(mismatched);
        CurrentPriceService service = new CurrentPriceService(provider, cache);

        CurrentPriceLookupResult result = service.getCurrentPrice("005930", () -> {});

        assertThat(result.snapshot()).isEqualTo(mismatched);
        assertThat(result.source()).isEqualTo(CurrentPriceLookupSource.PROVIDER);
        verify(cache, never()).put(eq("005930"), any());
    }

    @Test
    void doesNotCacheProviderResultWithNonPositivePrice() {
        CurrentPriceProvider provider = mock(CurrentPriceProvider.class);
        CurrentPriceCache cache = mock(CurrentPriceCache.class);
        CurrentPriceSnapshot invalid = new CurrentPriceSnapshot("005930", 0L, OBSERVED_AT);
        when(cache.get("005930")).thenReturn(Optional.empty());
        when(provider.getCurrentPrice("005930")).thenReturn(invalid);
        CurrentPriceService service = new CurrentPriceService(provider, cache);

        CurrentPriceLookupResult result = service.getCurrentPrice("005930", () -> {});

        assertThat(result.snapshot()).isEqualTo(invalid);
        assertThat(result.source()).isEqualTo(CurrentPriceLookupSource.PROVIDER);
        verify(cache, never()).put(eq("005930"), any());
    }

    @Test
    void propagatesProviderException() {
        CurrentPriceProvider provider = mock(CurrentPriceProvider.class);
        CurrentPriceCache cache = mock(CurrentPriceCache.class);
        when(cache.get("005930")).thenReturn(Optional.empty());
        when(provider.getCurrentPrice("005930"))
                .thenThrow(new IllegalStateException("Broker timeout"));
        CurrentPriceService service = new CurrentPriceService(provider, cache);

        assertThatThrownBy(() -> service.getCurrentPrice("005930", () -> {}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Broker timeout");
        verify(cache, never()).put(eq("005930"), any());
    }

    @Test
    void doesNotCacheProviderResultWithoutObservedAt() {
        CurrentPriceProvider provider = mock(CurrentPriceProvider.class);
        CurrentPriceCache cache = mock(CurrentPriceCache.class);
        CurrentPriceSnapshot invalid = new CurrentPriceSnapshot("005930", 70_000L, null);
        when(cache.get("005930")).thenReturn(Optional.empty());
        when(provider.getCurrentPrice("005930")).thenReturn(invalid);
        CurrentPriceService service = new CurrentPriceService(provider, cache);

        CurrentPriceLookupResult result = service.getCurrentPrice("005930", () -> {});

        assertThat(result.snapshot()).isEqualTo(invalid);
        assertThat(result.source()).isEqualTo(CurrentPriceLookupSource.PROVIDER);
        verify(cache, never()).put(eq("005930"), any());
    }

    @Test
    void doesNotCallProviderWhenProviderCallGuardRejectsCacheMiss() {
        CurrentPriceProvider provider = mock(CurrentPriceProvider.class);
        CurrentPriceCache cache = mock(CurrentPriceCache.class);
        when(cache.get("005930")).thenReturn(Optional.empty());
        CurrentPriceService service = new CurrentPriceService(provider, cache);

        assertThatThrownBy(() -> service.getCurrentPrice(
                "005930",
                () -> {
                    throw new IllegalStateException("Provider call denied");
                }
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Provider call denied");
        verify(provider, never()).getCurrentPrice("005930");
        verify(cache, never()).put(eq("005930"), any());
    }
}
