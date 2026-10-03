package com.stock.strategy.universe.liquidity.evaluation.snapshot.support;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverageCalculator;
import com.stock.strategy.universe.liquidity.evaluation.DailyTradingValueSelectionEvaluationService;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.DailyTradingValueSelectionSnapshot;
import com.stock.strategy.universe.liquidity.ranking.DailyTradingValueRankingPolicy;
import com.stock.strategy.universe.liquidity.selection.DailyTradingValueSelectionPolicy;

import java.time.LocalDate;
import java.util.List;

public final class DailyTradingValueSelectionSnapshotFixture {
    public static final LocalDate SELECTION_DATE = LocalDate.of(2026, 9, 23);
    public static final List<LocalDate> TRADING_DATES = List.of(
            SELECTION_DATE.minusDays(2), SELECTION_DATE.minusDays(1), SELECTION_DATE
    );

    private DailyTradingValueSelectionSnapshotFixture() {
    }

    public static DailyTradingValueSelectionSnapshot completeSnapshot() {
        return evaluate(request(), List.of(
                history("005930", 300L), history("000660", 200L),
                history("035420", 150L), history("005380", 0L)
        ));
    }

    public static DailyTradingValueSelectionSnapshot incompleteSnapshot() {
        return evaluate(request(), List.of(
                history("005930", 100L), new DailyPriceHistory("000660", List.of()),
                new DailyPriceHistory("005380", List.of(
                        bar(TRADING_DATES.getFirst(), 100L, TradingVenueScope.INTEGRATED),
                        bar(TRADING_DATES.get(1), null, null),
                        bar(SELECTION_DATE, 0L, TradingVenueScope.INTEGRATED)
                ))
        ));
    }

    public static DailyTradingValueSelectionSnapshot evaluate(
            DailyTradingValueSelectionEvaluationRequest request, List<DailyPriceHistory> histories
    ) {
        DailyTradingValueSelectionEvaluationService evaluator = new DailyTradingValueSelectionEvaluationService(
                new DailyTradingValueAverageCalculator(),
                new DailyTradingValueSelectionPolicy(new DailyTradingValueRankingPolicy())
        );
        return DailyTradingValueSelectionSnapshot.from(evaluator.evaluate(request, histories));
    }

    public static DailyPriceBar bar(LocalDate date, Long value, TradingVenueScope venue) {
        return new DailyPriceBar(date, 100L, 100L, 100L, 100L, 1L, value, venue);
    }

    private static DailyTradingValueSelectionEvaluationRequest request() {
        return new DailyTradingValueSelectionEvaluationRequest(
                List.of("035420", "005930", "000660", "005380"), SELECTION_DATE,
                TRADING_DATES, TradingVenueScope.INTEGRATED, 100L, 2
        );
    }

    private static DailyPriceHistory history(String symbol, long value) {
        return new DailyPriceHistory(symbol, TRADING_DATES.stream()
                .map(date -> bar(date, value, TradingVenueScope.INTEGRATED)).toList());
    }
}
