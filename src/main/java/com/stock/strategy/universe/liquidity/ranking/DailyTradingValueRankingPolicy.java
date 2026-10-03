package com.stock.strategy.universe.liquidity.ranking;

import com.stock.strategy.universe.liquidity.DailyTradingValueAverage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class DailyTradingValueRankingPolicy {
    public List<DailyTradingValueAverage> rank(List<DailyTradingValueAverage> averages) {
        Objects.requireNonNull(averages, "averages must not be null.");
        List<DailyTradingValueAverage> ranked = new ArrayList<>(averages);
        if (ranked.isEmpty()) {
            return List.of();
        }

        DailyTradingValueAverage reference = Objects.requireNonNull(
                ranked.getFirst(), "average must not be null."
        );
        Set<String> symbols = new HashSet<>();
        for (DailyTradingValueAverage average : ranked) {
            Objects.requireNonNull(average, "average must not be null.");
            if (!symbols.add(average.symbol())) {
                throw new IllegalArgumentException(
                        "Duplicate symbol in averages: " + average.symbol()
                );
            }
            if (!average.selectionAsOfDate().equals(reference.selectionAsOfDate())) {
                throw new IllegalArgumentException(
                        "selectionAsOfDate must match across averages."
                );
            }
            if (!average.tradingDates().equals(reference.tradingDates())) {
                throw new IllegalArgumentException("tradingDates must match across averages.");
            }
            if (average.tradingVenueScope() != reference.tradingVenueScope()) {
                throw new IllegalArgumentException(
                        "tradingVenueScope must match across averages."
                );
            }
        }

        // Equal trading-date lists give equal denominators, so exact totals rank averages.
        ranked.sort(Comparator
                .comparing(DailyTradingValueAverage::totalTradingValueKrw)
                .reversed()
                .thenComparing(DailyTradingValueAverage::symbol));
        return List.copyOf(ranked);
    }
}
