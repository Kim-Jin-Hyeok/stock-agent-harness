package com.stock.strategy.universe.candidate.evaluation.query;

import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
import com.stock.strategy.universe.candidate.evaluation.StockCandidateEvaluationService;
import com.stock.strategy.universe.candidate.evaluation.request.StockCandidateEvaluationRequest;
import com.stock.strategy.universe.candidate.evaluation.result.StockCandidateEvaluationResult;
import com.stock.strategy.universe.eligibility.input.StockEligibilityInput;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class StockCandidateEvaluationQueryService {
    private final DailyPriceHistoryQueryService historyQueryService;
    private final StockCandidateEvaluationService evaluationService;

    public StockCandidateEvaluationQueryService(
            DailyPriceHistoryQueryService historyQueryService,
            StockCandidateEvaluationService evaluationService
    ) {
        this.historyQueryService = Objects.requireNonNull(
                historyQueryService, "historyQueryService must not be null."
        );
        this.evaluationService = Objects.requireNonNull(evaluationService, "evaluationService must not be null.");
    }

    public StockCandidateEvaluationResult evaluate(
            StockCandidateEvaluationRequest request,
            List<StockEligibilityInput> eligibilityInputs
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        List<StockEligibilityInput> inputs = copyValidatedInputs(request, eligibilityInputs);
        LocalDate fromDate = request.liquidityRequest().requiredTradingDates().getFirst();
        LocalDate toDate = request.liquidityRequest().selectionAsOfDate();
        List<DailyPriceHistory> histories = new ArrayList<>(request.targetSymbols().size());
        for (String symbol : request.targetSymbols()) {
            DailyPriceHistory history = Objects.requireNonNull(
                    historyQueryService.getDailyPriceHistory(new DailyPriceHistoryRequest(symbol, fromDate, toDate)),
                    "Stored daily price history must not be null."
            );
            if (!symbol.equals(history.symbol())) {
                throw new IllegalStateException(
                        "Stored daily price history symbol must match request symbol. expected="
                                + symbol + ", actual=" + history.symbol()
                );
            }
            // Keep empty and ineligible histories; only the existing evaluator decides eligibility.
            histories.add(history);
        }
        return Objects.requireNonNull(
                evaluationService.evaluate(request, inputs, List.copyOf(histories)),
                "Candidate evaluation must not return null."
        );
    }

    private static List<StockEligibilityInput> copyValidatedInputs(
            StockCandidateEvaluationRequest request,
            List<StockEligibilityInput> eligibilityInputs
    ) {
        List<StockEligibilityInput> copied = List.copyOf(Objects.requireNonNull(
                eligibilityInputs, "eligibilityInputs must not be null."
        ));
        Set<String> targets = new HashSet<>(request.targetSymbols());
        Set<String> symbols = new HashSet<>();
        for (StockEligibilityInput input : copied) {
            if (!targets.contains(input.symbol())) {
                throw new IllegalArgumentException("Eligibility input symbol must belong to targetSymbols: "
                        + input.symbol());
            }
            if (!symbols.add(input.symbol())) {
                throw new IllegalArgumentException("Duplicate eligibility input symbol: " + input.symbol());
            }
        }
        return copied;
    }
}
