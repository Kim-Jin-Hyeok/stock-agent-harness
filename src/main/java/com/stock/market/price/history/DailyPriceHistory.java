package com.stock.market.price.history;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record DailyPriceHistory(
        String symbol,
        List<DailyPriceBar> bars
) {
    public DailyPriceHistory {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        bars = normalizeBars(bars);
    }

    private static List<DailyPriceBar> normalizeBars(
            List<DailyPriceBar> bars
    ) {
        Objects.requireNonNull(bars, "bars must not be null.");

        List<DailyPriceBar> sortedBars = new ArrayList<>(bars.size());
        for (DailyPriceBar bar : bars) {
            sortedBars.add(Objects.requireNonNull(
                    bar,
                    "bar must not be null."
            ));
        }
        sortedBars.sort(Comparator.comparing(DailyPriceBar::tradingDate));

        for (int index = 1; index < sortedBars.size(); index++) {
            if (sortedBars.get(index - 1).tradingDate()
                    .equals(sortedBars.get(index).tradingDate())) {
                throw new IllegalArgumentException(
                        "bars must not contain duplicate tradingDate."
                );
            }
        }

        return List.copyOf(sortedBars);
    }
}
