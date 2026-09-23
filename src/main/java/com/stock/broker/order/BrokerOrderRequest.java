package com.stock.broker.order;

import java.util.Objects;

public record BrokerOrderRequest(
        BrokerOrderSide side,
        String symbol,
        long quantity,
        long limitPriceKrw
) {
    public BrokerOrderRequest {
        Objects.requireNonNull(side, "side must not be null.");
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive.");
        }
        if (limitPriceKrw <= 0) {
            throw new IllegalArgumentException("limitPriceKrw must be positive.");
        }
    }
}
