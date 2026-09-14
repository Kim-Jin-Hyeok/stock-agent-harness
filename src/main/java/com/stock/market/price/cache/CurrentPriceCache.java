package com.stock.market.price.cache;

import com.stock.market.price.CurrentPriceSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
public class CurrentPriceCache {
    private final CurrentPriceCacheProperties properties;
    private final Clock clock;
    private final ConcurrentMap<String, CacheEntry> entries = new ConcurrentHashMap<>();

    public Optional<CurrentPriceSnapshot> get(String symbol) {
        CacheEntry entry = entries.get(symbol);

        if (entry == null) {
            return Optional.empty();
        }

        if (isExpired(entry, clock.instant())) {
            entries.remove(symbol, entry);
            return Optional.empty();
        }

        return Optional.of(entry.snapshot());
    }

    public void put(String symbol, CurrentPriceSnapshot snapshot) {
        Instant now = clock.instant();
        removeExpiredEntries(now);
        entries.put(symbol, new CacheEntry(snapshot, now));
    }

    int entryCount() {
        return entries.size();
    }

    private void removeExpiredEntries(Instant now) {
        entries.forEach((symbol, entry) -> {
            if (isExpired(entry, now)) {
                entries.remove(symbol, entry);
            }
        });
    }

    private boolean isExpired(CacheEntry entry, Instant now) {
        Instant expiresAt = entry.cachedAt().plus(properties.ttl());
        return !now.isBefore(expiresAt);
    }

    private record CacheEntry(
            CurrentPriceSnapshot snapshot,
            Instant cachedAt
    ) {
    }
}
