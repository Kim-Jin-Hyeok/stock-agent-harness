package com.stock.strategy.universe.liquidity.evaluation.request;

import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record DailyTradingValueSelectionEvaluationRequest(
        List<String> targetSymbols,
        LocalDate selectionAsOfDate,
        List<LocalDate> requiredTradingDates,
        TradingVenueScope expectedVenueScope,
        long minimumAverageTradingValueKrw,
        int maxCandidateCount
) {
    public DailyTradingValueSelectionEvaluationRequest {
        requiredTradingDates = DailyTradingValueAverage.copyValidatedTradingDates(
                selectionAsOfDate, requiredTradingDates
        );
        Objects.requireNonNull(expectedVenueScope, "expectedVenueScope must not be null.");
        if (minimumAverageTradingValueKrw <= 0) {
            throw new IllegalArgumentException("minimumAverageTradingValueKrw must be positive.");
        }
        if (maxCandidateCount <= 0) {
            throw new IllegalArgumentException("maxCandidateCount must be positive.");
        }
        targetSymbols = copyValidatedTargetSymbols(targetSymbols);
    }

    private static List<String> copyValidatedTargetSymbols(List<String> targetSymbols) {
        Objects.requireNonNull(targetSymbols, "targetSymbols must not be null.");
        List<String> symbols = new ArrayList<>(targetSymbols);
        if (symbols.isEmpty()) {
            throw new IllegalArgumentException("targetSymbols must not be empty.");
        }
        Set<String> unique = new HashSet<>();
        for (String symbol : symbols) {
            if (symbol == null || symbol.isBlank()) {
                throw new IllegalArgumentException("targetSymbols must not contain blank symbol.");
            }
            if (!unique.add(symbol)) {
                throw new IllegalArgumentException("Duplicate target symbol: " + symbol);
            }
        }
        symbols.sort(String::compareTo);
        return List.copyOf(symbols);
    }
}
