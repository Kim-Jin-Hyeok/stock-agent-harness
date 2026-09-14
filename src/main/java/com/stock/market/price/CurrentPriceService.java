package com.stock.market.price;

import com.stock.market.price.cache.CurrentPriceCache;
import com.stock.market.price.provider.CurrentPriceProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentPriceService {
    private final CurrentPriceProvider currentPriceProvider;
    private final CurrentPriceCache currentPriceCache;

    public CurrentPriceSnapshot getCurrentPrice(String symbol) {
        return currentPriceCache.get(symbol)
                .orElseGet(() -> loadAndCache(symbol));
    }

    private CurrentPriceSnapshot loadAndCache(String symbol) {
        CurrentPriceSnapshot snapshot = currentPriceProvider.getCurrentPrice(symbol);
        currentPriceCache.put(symbol, snapshot);
        return snapshot;
    }
}
