package com.stock.harness.persistence;

import com.stock.market.price.history.DailyPriceBar;

import java.time.LocalDate;

public record HarnessDailyPriceBarSnapshot(
        LocalDate tradingDate,
        long openPriceKrw,
        long highPriceKrw,
        long lowPriceKrw,
        long closePriceKrw,
        long volume
) {
    public static HarnessDailyPriceBarSnapshot from(DailyPriceBar bar) {
        return new HarnessDailyPriceBarSnapshot(
                bar.tradingDate(),
                bar.openPriceKrw(),
                bar.highPriceKrw(),
                bar.lowPriceKrw(),
                bar.closePriceKrw(),
                bar.volume()
        );
    }
}
