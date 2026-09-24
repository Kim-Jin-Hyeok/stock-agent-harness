package com.stock.market.price.history;

import java.time.LocalDate;
import java.util.Objects;

public record DailyPriceBar(
        LocalDate tradingDate,
        long openPriceKrw,
        long highPriceKrw,
        long lowPriceKrw,
        long closePriceKrw,
        long volume
) {
    public DailyPriceBar {
        Objects.requireNonNull(tradingDate, "tradingDate must not be null.");
        if (openPriceKrw <= 0) {
            throw new IllegalArgumentException("openPriceKrw must be positive.");
        }
        if (highPriceKrw <= 0) {
            throw new IllegalArgumentException("highPriceKrw must be positive.");
        }
        if (lowPriceKrw <= 0) {
            throw new IllegalArgumentException("lowPriceKrw must be positive.");
        }
        if (closePriceKrw <= 0) {
            throw new IllegalArgumentException("closePriceKrw must be positive.");
        }
        if (highPriceKrw < openPriceKrw
                || highPriceKrw < lowPriceKrw
                || highPriceKrw < closePriceKrw) {
            throw new IllegalArgumentException(
                    "highPriceKrw must not be lower than another price."
            );
        }
        if (lowPriceKrw > openPriceKrw
                || lowPriceKrw > highPriceKrw
                || lowPriceKrw > closePriceKrw) {
            throw new IllegalArgumentException(
                    "lowPriceKrw must not be higher than another price."
            );
        }
        if (volume < 0) {
            throw new IllegalArgumentException("volume must not be negative.");
        }
    }
}
