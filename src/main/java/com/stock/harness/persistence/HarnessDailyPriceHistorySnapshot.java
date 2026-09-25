package com.stock.harness.persistence;

import com.stock.market.price.history.DailyPriceHistory;

import java.util.List;

public record HarnessDailyPriceHistorySnapshot(
        String symbol,
        List<HarnessDailyPriceBarSnapshot> bars
) {
    public HarnessDailyPriceHistorySnapshot {
        bars = List.copyOf(bars);
    }

    public static HarnessDailyPriceHistorySnapshot from(
            DailyPriceHistory history
    ) {
        return new HarnessDailyPriceHistorySnapshot(
                history.symbol(),
                history.bars().stream()
                        .map(HarnessDailyPriceBarSnapshot::from)
                        .toList()
        );
    }
}
