package com.stock.strategy.universe.liquidity.evaluation.query;

import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
import com.stock.strategy.universe.liquidity.evaluation.DailyTradingValueSelectionEvaluationService;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationResult;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class DailyTradingValueSelectionQueryService {
    private final DailyPriceHistoryQueryService historyQueryService;
    private final DailyTradingValueSelectionEvaluationService evaluationService;

    public DailyTradingValueSelectionQueryService(
            DailyPriceHistoryQueryService historyQueryService,
            DailyTradingValueSelectionEvaluationService evaluationService
    ) {
        this.historyQueryService = Objects.requireNonNull(
                historyQueryService, "historyQueryService must not be null."
        );
        this.evaluationService = Objects.requireNonNull(
                evaluationService, "evaluationService must not be null."
        );
    }

    public DailyTradingValueSelectionEvaluationResult evaluate(
            DailyTradingValueSelectionEvaluationRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        LocalDate fromDate = request.requiredTradingDates().getFirst();
        LocalDate toDate = request.selectionAsOfDate();
        List<DailyPriceHistory> histories = new ArrayList<>(request.targetSymbols().size());
        for (String symbol : request.targetSymbols()) {
            DailyPriceHistory history = Objects.requireNonNull(
                    historyQueryService.getDailyPriceHistory(
                            new DailyPriceHistoryRequest(symbol, fromDate, toDate)
                    ),
                    "Stored daily price history must not be null."
            );
            if (!symbol.equals(history.symbol())) {
                throw new IllegalStateException(
                        "Stored daily price history symbol must match request symbol. expected="
                                + symbol + ", actual=" + history.symbol()
                );
            }
            // Empty histories still represent targets; query failures must propagate.
            histories.add(history);
        }
        return evaluationService.evaluate(request, List.copyOf(histories));
    }
}
