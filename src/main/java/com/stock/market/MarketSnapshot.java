package com.stock.market;

public record MarketSnapshot(
        String market,
        boolean marketOpen,
        String description
) {
}
