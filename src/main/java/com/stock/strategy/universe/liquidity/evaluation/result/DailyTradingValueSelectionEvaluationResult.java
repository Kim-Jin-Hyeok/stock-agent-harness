package com.stock.strategy.universe.liquidity.evaluation.result;

import com.stock.strategy.universe.liquidity.DailyTradingValueAverage;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import com.stock.strategy.universe.liquidity.selection.result.DailyTradingValueSelectionResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record DailyTradingValueSelectionEvaluationResult(
        DailyTradingValueSelectionEvaluationRequest request,
        DailyTradingValueSelectionEvaluationStatus status,
        List<DailyTradingValueAverage> calculatedAverages,
        List<String> unverifiedSymbols,
        List<DailyTradingValueSelectionResult> selectionResults
) {
    public DailyTradingValueSelectionEvaluationResult {
        Objects.requireNonNull(request, "request must not be null.");
        Objects.requireNonNull(status, "status must not be null.");
        calculatedAverages = List.copyOf(Objects.requireNonNull(
                calculatedAverages, "calculatedAverages must not be null."
        ));
        unverifiedSymbols = List.copyOf(Objects.requireNonNull(
                unverifiedSymbols, "unverifiedSymbols must not be null."
        ));
        selectionResults = List.copyOf(Objects.requireNonNull(
                selectionResults, "selectionResults must not be null."
        ));

        Map<String, DailyTradingValueAverage> averagesBySymbol =
                validateCalculatedAverages(request, calculatedAverages);
        Set<String> unverified = new HashSet<>();
        for (String symbol : unverifiedSymbols) {
            if (symbol.isBlank()) {
                throw new IllegalArgumentException(
                        "unverifiedSymbols must not contain blank symbol."
                );
            }
            if (!unverified.add(symbol)) {
                throw new IllegalArgumentException("Duplicate unverified symbol: " + symbol);
            }
            if (averagesBySymbol.containsKey(symbol)) {
                throw new IllegalArgumentException(
                        "Calculated and unverified symbols must not overlap: " + symbol
                );
            }
        }

        if (status == DailyTradingValueSelectionEvaluationStatus.INCOMPLETE) {
            if (unverifiedSymbols.isEmpty()) {
                throw new IllegalArgumentException("INCOMPLETE status requires unverified symbols.");
            }
            if (!selectionResults.isEmpty()) {
                throw new IllegalArgumentException(
                        "INCOMPLETE status must not contain selection results."
                );
            }
        } else {
            if (!unverifiedSymbols.isEmpty()) {
                throw new IllegalArgumentException("COMPLETE status must not contain unverified symbols.");
            }
            if (calculatedAverages.isEmpty()) {
                throw new IllegalArgumentException("COMPLETE status requires calculated averages.");
            }
        }

        Set<String> classifiedSymbols = new HashSet<>(averagesBySymbol.keySet());
        classifiedSymbols.addAll(unverified);
        if (!classifiedSymbols.equals(new HashSet<>(request.targetSymbols()))) {
            throw new IllegalArgumentException(
                    "Calculated and unverified symbols must exactly cover request targetSymbols."
            );
        }
        if (status == DailyTradingValueSelectionEvaluationStatus.COMPLETE) {
            validateSelectionResults(averagesBySymbol, selectionResults);
        }
    }

    private static Map<String, DailyTradingValueAverage> validateCalculatedAverages(
            DailyTradingValueSelectionEvaluationRequest request,
            List<DailyTradingValueAverage> averages
    ) {
        Map<String, DailyTradingValueAverage> bySymbol = new HashMap<>();
        for (DailyTradingValueAverage average : averages) {
            if (bySymbol.putIfAbsent(average.symbol(), average) != null) {
                throw new IllegalArgumentException("Duplicate calculated symbol: " + average.symbol());
            }
            if (!average.selectionAsOfDate().equals(request.selectionAsOfDate())
                    || !average.tradingDates().equals(request.requiredTradingDates())
                    || average.tradingVenueScope() != request.expectedVenueScope()) {
                throw new IllegalArgumentException(
                        "Calculated average must match request trading window and venue. symbol="
                                + average.symbol()
                );
            }
        }
        return bySymbol;
    }

    private static void validateSelectionResults(
            Map<String, DailyTradingValueAverage> averagesBySymbol,
            List<DailyTradingValueSelectionResult> results
    ) {
        if (results.size() != averagesBySymbol.size()) {
            throw new IllegalArgumentException("selectionResults must cover every calculated average.");
        }
        Set<String> selectedSymbols = new HashSet<>();
        for (int index = 0; index < results.size(); index++) {
            DailyTradingValueSelectionResult result = results.get(index);
            String symbol = result.average().symbol();
            if (!selectedSymbols.add(symbol)) {
                throw new IllegalArgumentException("Duplicate selection result symbol: " + symbol);
            }
            if (!result.average().equals(averagesBySymbol.get(symbol))) {
                throw new IllegalArgumentException("selectionResults must preserve calculated averages.");
            }
            if (result.rank() != index + 1) {
                throw new IllegalArgumentException(
                        "selectionResults must have consecutive ranks starting at one."
                );
            }
        }
    }
}
