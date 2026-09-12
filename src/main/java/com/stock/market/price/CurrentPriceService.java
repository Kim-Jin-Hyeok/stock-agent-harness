package com.stock.market.price;

import org.springframework.stereotype.Component;

@Component
public class CurrentPriceService {
    private static final long FIXED_CURRENT_PRICE_KRW = 100_000L;

    public CurrentPriceSnapshot getCurrentPrice(String symbol) {
        return new CurrentPriceSnapshot(symbol, FIXED_CURRENT_PRICE_KRW);
    }
}
