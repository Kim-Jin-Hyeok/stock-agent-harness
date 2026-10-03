package com.stock.strategy.universe.liquidity.evaluation;

import com.stock.market.price.history.DailyPriceHistory;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverage;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverageCalculator;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationResult;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationStatus;
import com.stock.strategy.universe.liquidity.selection.DailyTradingValueSelectionPolicy;
import org.springframework.stereotype.Service;

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
            DailyTradingValueSelectionEvaluationRequest request,
            List<DailyPriceHistory> histories
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        List<String> symbols = request.targetSymbols();
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
                    history, request.selectionAsOfDate(), request.requiredTradingDates(),
                    request.expectedVenueScope()
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
                    request, DailyTradingValueSelectionEvaluationStatus.INCOMPLETE,
                    calculatedAverages, unverifiedSymbols, List.of()
            );
        }
        return new DailyTradingValueSelectionEvaluationResult(
                request, DailyTradingValueSelectionEvaluationStatus.COMPLETE,
                calculatedAverages, List.of(),
                selectionPolicy.select(
                        calculatedAverages, request.minimumAverageTradingValueKrw(),
                        request.maxCandidateCount()
                )
        );
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
