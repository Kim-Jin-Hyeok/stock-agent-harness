package com.stock.market.price.provider;

import com.stock.market.price.CurrentPriceSnapshot;
import org.springframework.stereotype.Component;

@Component
public class FixedCurrentPriceProvider implements CurrentPriceProvider {
    private static final long FIXED_CURRENT_PRICE_KRW = 100_000L;

    @Override
    public CurrentPriceSnapshot getCurrentPrice(String symbol) {
        return new CurrentPriceSnapshot(symbol, FIXED_CURRENT_PRICE_KRW);
    }
}
