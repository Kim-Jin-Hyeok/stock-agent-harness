package com.stock.harness.persistence;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.TradingVenueScope;

import java.time.LocalDate;

public record HarnessDailyPriceBarSnapshot(
        LocalDate tradingDate,
        long openPriceKrw,
        long highPriceKrw,
        long lowPriceKrw,
        long closePriceKrw,
        long volume,
        Long tradingValueKrw,
        TradingVenueScope tradingVenueScope
) {
    public HarnessDailyPriceBarSnapshot(
            LocalDate tradingDate,
            long openPriceKrw,
            long highPriceKrw,
            long lowPriceKrw,
            long closePriceKrw,
            long volume
    ) {
        this(tradingDate, openPriceKrw, highPriceKrw, lowPriceKrw,
                closePriceKrw, volume, null, null);
    }

    public static HarnessDailyPriceBarSnapshot from(DailyPriceBar bar) {
        return new HarnessDailyPriceBarSnapshot(
                bar.tradingDate(),
                bar.openPriceKrw(),
                bar.highPriceKrw(),
                bar.lowPriceKrw(),
                bar.closePriceKrw(),
                bar.volume(),
                bar.tradingValueKrw(),
                bar.tradingVenueScope()
        );
    }
}
