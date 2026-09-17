package com.stock.market.price.cache;

import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.validation.CurrentPriceFreshnessPolicy;
import com.stock.market.price.validation.CurrentPriceFreshnessProperties;
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
    private static final Duration MAX_AGE = Duration.ofMinutes(1);

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
                clock,
                freshnessPolicy()
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
                clock,
                freshnessPolicy()
        );
        CurrentPriceSnapshot skHynix = new CurrentPriceSnapshot("000660", 120_000L, OBSERVED_AT);
        cache.put("005930", new CurrentPriceSnapshot("005930", 70_000L, OBSERVED_AT));

        cache.put("000660", skHynix);

        assertThat(cache.entryCount()).isEqualTo(1);
        assertThat(cache.get("000660")).contains(skHynix);
    }

    @Test
    void returnsEmptyWhenCurrentPriceFreshnessExpiresBeforeTtl() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(CACHED_AT, OBSERVED_AT.plus(MAX_AGE));
        CurrentPriceCache cache = new CurrentPriceCache(
                new CurrentPriceCacheProperties(Duration.ofMinutes(5)),
                clock,
                freshnessPolicy()
        );
        cache.put("005930", new CurrentPriceSnapshot("005930", 70_000L, OBSERVED_AT));

        assertThat(cache.get("005930")).isEmpty();
    }

    private CurrentPriceCache cache(Instant instant) {
        return new CurrentPriceCache(
                new CurrentPriceCacheProperties(TTL),
                Clock.fixed(instant, ZoneOffset.UTC),
                freshnessPolicy()
        );
    }

    private CurrentPriceFreshnessPolicy freshnessPolicy() {
        return new CurrentPriceFreshnessPolicy(
                new CurrentPriceFreshnessProperties(MAX_AGE),
                Clock.fixed(CACHED_AT, ZoneOffset.UTC)
        );
    }
}
