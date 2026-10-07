package com.stock.market.stock.basicinfo.collection;

import com.stock.market.stock.basicinfo.observation.storage.KisStockBasicInfoObservationStore;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoProvider;

import java.util.Objects;

public class KisStockBasicInfoCollectionService {
    private final KisStockBasicInfoProvider provider;
    private final KisStockBasicInfoObservationStore store;

    public KisStockBasicInfoCollectionService(KisStockBasicInfoProvider provider, KisStockBasicInfoObservationStore store) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null.");
        this.store = Objects.requireNonNull(store, "store must not be null.");
    }

    public Long collect(String symbol) {
        var response = provider.getStockBasicInfo(symbol);
        if (response == null) {
            throw new IllegalStateException("KIS stock basic info response must not be null.");
        }
        if (!response.requestedSymbol().equals(symbol)) {
            throw new IllegalStateException("KIS stock basic info response requested symbol does not match collection request.");
        }
        return store.save(response);
    }
}
