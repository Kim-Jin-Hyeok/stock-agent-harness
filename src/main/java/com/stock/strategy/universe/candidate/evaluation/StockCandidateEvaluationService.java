package com.stock.strategy.universe.candidate.evaluation;

import com.stock.market.price.history.DailyPriceHistory;
import com.stock.strategy.universe.candidate.evaluation.request.StockCandidateEvaluationRequest;
import com.stock.strategy.universe.candidate.evaluation.result.StockCandidateEvaluationResult;
import com.stock.strategy.universe.candidate.evaluation.result.StockCandidateEvaluationStatus;
import com.stock.strategy.universe.eligibility.StockEligibilityPolicy;
import com.stock.strategy.universe.eligibility.input.StockEligibilityEvidenceStatus;
import com.stock.strategy.universe.eligibility.input.StockEligibilityInput;
import com.stock.strategy.universe.eligibility.result.StockEligibilityResult;
import com.stock.strategy.universe.eligibility.result.StockEligibilityStatus;
import com.stock.strategy.universe.liquidity.evaluation.DailyTradingValueSelectionEvaluationService;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationResult;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class StockCandidateEvaluationService {
    private final StockEligibilityPolicy eligibilityPolicy;
    private final DailyTradingValueSelectionEvaluationService liquidityEvaluationService;

    public StockCandidateEvaluationService(
            StockEligibilityPolicy eligibilityPolicy,
            DailyTradingValueSelectionEvaluationService liquidityEvaluationService
    ) {
        this.eligibilityPolicy = Objects.requireNonNull(eligibilityPolicy, "eligibilityPolicy must not be null.");
        this.liquidityEvaluationService = Objects.requireNonNull(
                liquidityEvaluationService, "liquidityEvaluationService must not be null."
        );
    }

    public StockCandidateEvaluationResult evaluate(
            StockCandidateEvaluationRequest request,
            List<StockEligibilityInput> eligibilityInputs,
            List<DailyPriceHistory> histories
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        Set<String> targets = new HashSet<>(request.targetSymbols());
        Map<String, StockEligibilityInput> inputsBySymbol = indexEligibilityInputs(eligibilityInputs, targets);
        Map<String, DailyPriceHistory> historiesBySymbol = indexHistories(histories, targets);
        List<DailyPriceHistory> inputHistories = request.targetSymbols().stream()
                .map(historiesBySymbol::get).filter(Objects::nonNull).toList();
        List<StockEligibilityResult> eligibilityResults = new ArrayList<>();
        for (String symbol : request.targetSymbols()) {
            StockEligibilityInput input = inputsBySymbol.get(symbol);
            if (input == null) {
                // Represent absence explicitly; never drop the target or invent past metadata.
                input = new StockEligibilityInput(
                        symbol, null, null, null, null, null, null, StockEligibilityEvidenceStatus.UNVERIFIED
                );
            }
            StockEligibilityResult result = Objects.requireNonNull(
                    eligibilityPolicy.evaluate(request.eligibilityRequest(), input),
                    "Eligibility evaluation must not return null."
            );
            if (!result.request().equals(request.eligibilityRequest()) || !result.input().equals(input)) {
                throw new IllegalArgumentException("Eligibility evaluation must preserve request and input.");
            }
            eligibilityResults.add(result);
        }

        if (eligibilityResults.stream().anyMatch(result -> result.status() == StockEligibilityStatus.DATA_UNVERIFIED)) {
            return new StockCandidateEvaluationResult(
                    request, StockCandidateEvaluationStatus.INCOMPLETE, eligibilityResults, inputHistories, null
            );
        }
        List<String> eligibleSymbols = eligibilityResults.stream()
                .filter(result -> result.status() == StockEligibilityStatus.ELIGIBLE)
                .map(result -> result.input().symbol()).toList();
        if (eligibleSymbols.isEmpty()) {
            return new StockCandidateEvaluationResult(
                    request, StockCandidateEvaluationStatus.COMPLETE, eligibilityResults, inputHistories, null
            );
        }

        // Filter before ranking and the candidate limit, but retain every supplied history above.
        Set<String> eligible = new HashSet<>(eligibleSymbols);
        List<DailyPriceHistory> eligibleHistories = inputHistories.stream()
                .filter(history -> eligible.contains(history.symbol())).toList();
        DailyTradingValueSelectionEvaluationResult liquidityResult = Objects.requireNonNull(
                liquidityEvaluationService.evaluate(request.liquidityRequestFor(eligibleSymbols), eligibleHistories),
                "Liquidity evaluation must not return null."
        );
        StockCandidateEvaluationStatus status = liquidityResult.status()
                == DailyTradingValueSelectionEvaluationStatus.COMPLETE
                ? StockCandidateEvaluationStatus.COMPLETE : StockCandidateEvaluationStatus.INCOMPLETE;
        return new StockCandidateEvaluationResult(request, status, eligibilityResults, inputHistories, liquidityResult);
    }

    private static Map<String, StockEligibilityInput> indexEligibilityInputs(
            List<StockEligibilityInput> inputs,
            Set<String> targets
    ) {
        List<StockEligibilityInput> copied = List.copyOf(Objects.requireNonNull(
                inputs, "eligibilityInputs must not be null."
        ));
        Map<String, StockEligibilityInput> bySymbol = new HashMap<>();
        for (StockEligibilityInput input : copied) {
            if (!targets.contains(input.symbol())) {
                throw new IllegalArgumentException("Eligibility input symbol must belong to targetSymbols: "
                        + input.symbol());
            }
            if (bySymbol.putIfAbsent(input.symbol(), input) != null) {
                throw new IllegalArgumentException("Duplicate eligibility input symbol: " + input.symbol());
            }
        }
        return bySymbol;
    }

    private static Map<String, DailyPriceHistory> indexHistories(List<DailyPriceHistory> histories, Set<String> targets) {
        List<DailyPriceHistory> copied = List.copyOf(Objects.requireNonNull(histories, "histories must not be null."));
        Map<String, DailyPriceHistory> bySymbol = new HashMap<>();
        for (DailyPriceHistory history : copied) {
            if (!targets.contains(history.symbol())) {
                throw new IllegalArgumentException("History symbol must belong to targetSymbols: " + history.symbol());
            }
            if (bySymbol.putIfAbsent(history.symbol(), history) != null) {
                throw new IllegalArgumentException("Duplicate history symbol: " + history.symbol());
            }
        }
        return bySymbol;
    }
}
