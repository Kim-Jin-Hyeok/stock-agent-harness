package com.stock.market.price;

import com.stock.market.price.cache.CurrentPriceCache;
import com.stock.market.price.lookup.CurrentPriceLookupResult;
import com.stock.market.price.provider.CurrentPriceProvider;
import com.stock.market.price.provider.CurrentPriceProviderCallGuard;
import com.stock.market.price.validation.CurrentPriceFreshnessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentPriceService {
    private final CurrentPriceProvider currentPriceProvider;
    private final CurrentPriceCache currentPriceCache;
    private final CurrentPriceFreshnessPolicy currentPriceFreshnessPolicy;

    public CurrentPriceLookupResult getCurrentPrice(
            String symbol,
            CurrentPriceProviderCallGuard providerCallGuard
    ) {
        return currentPriceCache.get(symbol)
                .map(CurrentPriceLookupResult::cache)
                .orElseGet(() -> loadAndCache(symbol, providerCallGuard));
    }

    private CurrentPriceLookupResult loadAndCache(
            String symbol,
            CurrentPriceProviderCallGuard providerCallGuard
    ) {
        providerCallGuard.beforeCall();
        CurrentPriceSnapshot snapshot = currentPriceProvider.getCurrentPrice(symbol);

        if (isCacheable(symbol, snapshot)) {
            currentPriceCache.put(symbol, snapshot);
        }

        return CurrentPriceLookupResult.provider(snapshot);
    }

    private boolean isCacheable(String symbol, CurrentPriceSnapshot snapshot) {
        return snapshot != null
                && symbol.equals(snapshot.symbol())
                && snapshot.priceKrw() > 0
                && currentPriceFreshnessPolicy.isFresh(snapshot.observedAt());
    }
}
