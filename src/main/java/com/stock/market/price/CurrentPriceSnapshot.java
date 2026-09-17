package com.stock.market.price;

import java.time.Instant;

public record CurrentPriceSnapshot(
        String symbol,
        long priceKrw,
        Instant observedAt
) {
}
