package com.stock.market.price.lookup;

import com.stock.market.price.CurrentPriceSnapshot;

public record CurrentPriceLookupResult(
        CurrentPriceSnapshot snapshot,
        CurrentPriceLookupSource source
) {
    public static CurrentPriceLookupResult cache(CurrentPriceSnapshot snapshot) {
        return new CurrentPriceLookupResult(snapshot, CurrentPriceLookupSource.CACHE);
    }

    public static CurrentPriceLookupResult provider(CurrentPriceSnapshot snapshot) {
        return new CurrentPriceLookupResult(snapshot, CurrentPriceLookupSource.PROVIDER);
    }
}
