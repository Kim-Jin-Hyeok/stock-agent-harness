package com.stock.market.price.provider.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stock.market.price.CurrentPriceSnapshot;

import java.time.Instant;
import java.util.Objects;

public record KisCurrentPriceOutput(
        @JsonProperty("stck_prpr") String currentPrice
) {
    public CurrentPriceSnapshot toSnapshot(String symbol, Instant observedAt) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        Objects.requireNonNull(observedAt, "observedAt must not be null.");

        long priceKrw = parseCurrentPrice();
        if (priceKrw <= 0) {
            throw new IllegalArgumentException("currentPrice must be positive.");
        }

        return new CurrentPriceSnapshot(symbol, priceKrw, observedAt);
    }

    private long parseCurrentPrice() {
        if (currentPrice == null || currentPrice.isBlank()) {
            throw new IllegalArgumentException("currentPrice must not be blank.");
        }

        try {
            return Long.parseLong(currentPrice);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "currentPrice must be a number.",
                    exception
            );
        }
    }
}
