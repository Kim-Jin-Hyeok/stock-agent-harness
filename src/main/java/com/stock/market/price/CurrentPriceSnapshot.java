package com.stock.market.price;

public record CurrentPriceSnapshot(
        String symbol,
        long priceKrw
) {
}
