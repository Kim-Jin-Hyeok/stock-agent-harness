package com.stock.strategy.universe.liquidity.evaluation;

import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverage;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverageCalculator;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationResult;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationStatus;
import com.stock.strategy.universe.liquidity.selection.DailyTradingValueSelectionPolicy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class DailyTradingValueSelectionEvaluationService {
    private final DailyTradingValueAverageCalculator calculator;
    private final DailyTradingValueSelectionPolicy selectionPolicy;

    public DailyTradingValueSelectionEvaluationService(
            DailyTradingValueAverageCalculator calculator,
            DailyTradingValueSelectionPolicy selectionPolicy
    ) {
        this.calculator = Objects.requireNonNull(calculator, "calculator must not be null.");
        this.selectionPolicy = Objects.requireNonNull(
                selectionPolicy, "selectionPolicy must not be null."
        );
    }

    public DailyTradingValueSelectionEvaluationResult evaluate(
            List<String> targetSymbols,
            List<DailyPriceHistory> histories,
            LocalDate selectionAsOfDate,
            List<LocalDate> requiredTradingDates,
            TradingVenueScope expectedVenueScope,
            long minimumAverageTradingValueKrw,
            int maxCandidateCount
    ) {
        List<LocalDate> tradingDates = DailyTradingValueAverage.copyValidatedTradingDates(
                selectionAsOfDate, requiredTradingDates
        );
        Objects.requireNonNull(expectedVenueScope, "expectedVenueScope must not be null.");
        if (minimumAverageTradingValueKrw <= 0) {
            throw new IllegalArgumentException("minimumAverageTradingValueKrw must be positive.");
        }
        if (maxCandidateCount <= 0) {
            throw new IllegalArgumentException("maxCandidateCount must be positive.");
        }

        List<String> symbols = copyValidatedTargetSymbols(targetSymbols);
        Map<String, DailyPriceHistory> historiesBySymbol = indexHistories(
                histories, new HashSet<>(symbols)
        );
        List<DailyTradingValueAverage> calculatedAverages = new ArrayList<>();
        List<String> unverifiedSymbols = new ArrayList<>();
        for (String symbol : symbols) {
            DailyPriceHistory history = historiesBySymbol.get(symbol);
            if (history == null) {
                unverifiedSymbols.add(symbol);
                continue;
            }
            Optional<DailyTradingValueAverage> average = calculator.calculate(
                    history, selectionAsOfDate, tradingDates, expectedVenueScope
            );
            if (average.isPresent()) {
                calculatedAverages.add(average.orElseThrow());
            } else {
                unverifiedSymbols.add(symbol);
            }
        }

        // Keep successful calculations, but never select from a partial target set.
        if (!unverifiedSymbols.isEmpty()) {
            return new DailyTradingValueSelectionEvaluationResult(
                    DailyTradingValueSelectionEvaluationStatus.INCOMPLETE,
                    calculatedAverages, unverifiedSymbols, List.of()
            );
        }
        return new DailyTradingValueSelectionEvaluationResult(
                DailyTradingValueSelectionEvaluationStatus.COMPLETE,
                calculatedAverages, List.of(),
                selectionPolicy.select(
                        calculatedAverages, minimumAverageTradingValueKrw, maxCandidateCount
                )
        );
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

    private static Map<String, DailyPriceHistory> indexHistories(
            List<DailyPriceHistory> histories,
            Set<String> targetSymbols
    ) {
        Objects.requireNonNull(histories, "histories must not be null.");
        Map<String, DailyPriceHistory> bySymbol = new HashMap<>();
        for (DailyPriceHistory history : new ArrayList<>(histories)) {
            Objects.requireNonNull(history, "history must not be null.");
            if (!targetSymbols.contains(history.symbol())) {
                throw new IllegalArgumentException(
                        "History symbol must belong to targetSymbols: " + history.symbol()
                );
            }
            if (bySymbol.putIfAbsent(history.symbol(), history) != null) {
                throw new IllegalArgumentException("Duplicate history symbol: " + history.symbol());
            }
        }
        return bySymbol;
    }
}
