package com.stock.market.price.cache;

import com.stock.market.price.CurrentPriceSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentPriceCacheTest {
    private static final Instant CACHED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant OBSERVED_AT = CACHED_AT.minusSeconds(5);
    private static final Duration TTL = Duration.ofSeconds(30);

    @Test
    void returnsCachedCurrentPriceBeforeTtlExpires() {
        CurrentPriceCache cache = cache(CACHED_AT);
        CurrentPriceSnapshot snapshot = new CurrentPriceSnapshot(
                "005930",
                70_000L,
                OBSERVED_AT
        );
        cache.put("005930", snapshot);

        assertThat(cache.get("005930")).contains(snapshot);
    }

    @Test
    void returnsEmptyWhenCachedCurrentPriceExpires() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(CACHED_AT, CACHED_AT.plus(TTL));
        CurrentPriceCache cache = new CurrentPriceCache(
                new CurrentPriceCacheProperties(TTL),
                clock
        );
        cache.put("005930", new CurrentPriceSnapshot("005930", 70_000L, OBSERVED_AT));

        assertThat(cache.get("005930")).isEmpty();
    }

    @Test
    void keepsCurrentPricesSeparatedBySymbol() {
        CurrentPriceCache cache = cache(CACHED_AT);
        CurrentPriceSnapshot samsung = new CurrentPriceSnapshot("005930", 70_000L, OBSERVED_AT);
        CurrentPriceSnapshot skHynix = new CurrentPriceSnapshot("000660", 120_000L, OBSERVED_AT);
        cache.put("005930", samsung);
        cache.put("000660", skHynix);

        assertThat(cache.get("005930")).contains(samsung);
        assertThat(cache.get("000660")).contains(skHynix);
    }

    @Test
    void removesExpiredEntriesWhenCachingAnotherSymbol() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(CACHED_AT, CACHED_AT.plus(TTL));
        CurrentPriceCache cache = new CurrentPriceCache(
                new CurrentPriceCacheProperties(TTL),
                clock
        );
        CurrentPriceSnapshot skHynix = new CurrentPriceSnapshot("000660", 120_000L, OBSERVED_AT);
        cache.put("005930", new CurrentPriceSnapshot("005930", 70_000L, OBSERVED_AT));

        cache.put("000660", skHynix);

        assertThat(cache.entryCount()).isEqualTo(1);
        assertThat(cache.get("000660")).contains(skHynix);
    }

    private CurrentPriceCache cache(Instant instant) {
        return new CurrentPriceCache(
                new CurrentPriceCacheProperties(TTL),
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }
}
