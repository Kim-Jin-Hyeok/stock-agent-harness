package com.stock.strategy.universe.candidate.evaluation.result;

import com.stock.market.price.history.DailyPriceHistory;
import com.stock.strategy.universe.candidate.evaluation.request.StockCandidateEvaluationRequest;
import com.stock.strategy.universe.eligibility.result.StockEligibilityResult;
import com.stock.strategy.universe.eligibility.result.StockEligibilityStatus;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationResult;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationStatus;
import com.stock.strategy.universe.liquidity.selection.result.DailyTradingValueSelectionStatus;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record StockCandidateEvaluationResult(
        StockCandidateEvaluationRequest request,
        StockCandidateEvaluationStatus status,
        List<StockEligibilityResult> eligibilityResults,
        List<DailyPriceHistory> inputHistories,
        DailyTradingValueSelectionEvaluationResult liquidityResult
) {
    public StockCandidateEvaluationResult {
        Objects.requireNonNull(request, "request must not be null.");
        Objects.requireNonNull(status, "status must not be null.");
        eligibilityResults = validateEligibilityResults(request, eligibilityResults);
        inputHistories = validateInputHistories(request, inputHistories);

        boolean unverifiedEligibility = eligibilityResults.stream()
                .anyMatch(result -> result.status() == StockEligibilityStatus.DATA_UNVERIFIED);
        List<String> eligibleSymbols = eligibilityResults.stream()
                .filter(result -> result.status() == StockEligibilityStatus.ELIGIBLE)
                .map(result -> result.input().symbol()).toList();
        if (unverifiedEligibility) {
            if (status != StockCandidateEvaluationStatus.INCOMPLETE || liquidityResult != null) {
                throw new IllegalArgumentException(
                        "Unverified eligibility requires INCOMPLETE status and no liquidity result."
                );
            }
        } else if (eligibleSymbols.isEmpty()) {
            if (status != StockCandidateEvaluationStatus.COMPLETE || liquidityResult != null) {
                throw new IllegalArgumentException(
                        "All ineligible targets require COMPLETE status and no liquidity result."
                );
            }
        } else {
            Objects.requireNonNull(liquidityResult, "Eligible targets require a liquidity result.");
            if (!liquidityResult.request().equals(request.liquidityRequestFor(eligibleSymbols))) {
                throw new IllegalArgumentException("Liquidity request must match eligible targets and criteria.");
            }
            Set<String> eligible = new HashSet<>(eligibleSymbols);
            List<DailyPriceHistory> eligibleHistories = inputHistories.stream()
                    .filter(history -> eligible.contains(history.symbol())).toList();
            if (!liquidityResult.inputHistories().equals(eligibleHistories)) {
                throw new IllegalArgumentException("Liquidity result must preserve eligible input histories.");
            }
            StockCandidateEvaluationStatus expected = liquidityResult.status()
                    == DailyTradingValueSelectionEvaluationStatus.COMPLETE
                    ? StockCandidateEvaluationStatus.COMPLETE : StockCandidateEvaluationStatus.INCOMPLETE;
            if (status != expected) {
                throw new IllegalArgumentException("status must match liquidity evaluation status.");
            }
        }
    }

    public List<String> candidateSymbols() {
        if (status != StockCandidateEvaluationStatus.COMPLETE || liquidityResult == null) {
            return List.of();
        }
        return liquidityResult.selectionResults().stream()
                .filter(result -> result.status() == DailyTradingValueSelectionStatus.SELECTED)
                .map(result -> result.average().symbol()).toList();
    }

    public List<String> unverifiedSymbols() {
        if (liquidityResult != null) {
            return liquidityResult.unverifiedSymbols();
        }
        return eligibilityResults.stream()
                .filter(result -> result.status() == StockEligibilityStatus.DATA_UNVERIFIED)
                .map(result -> result.input().symbol()).toList();
    }

    private static List<StockEligibilityResult> validateEligibilityResults(
            StockCandidateEvaluationRequest request,
            List<StockEligibilityResult> results
    ) {
        List<StockEligibilityResult> copied = List.copyOf(Objects.requireNonNull(
                results, "eligibilityResults must not be null."
        ));
        Set<String> symbols = new HashSet<>();
        for (StockEligibilityResult result : copied) {
            if (!result.request().equals(request.eligibilityRequest())) {
                throw new IllegalArgumentException("Eligibility result request must match evaluation criteria.");
            }
            if (!symbols.add(result.input().symbol())) {
                throw new IllegalArgumentException("Duplicate eligibility result symbol: " + result.input().symbol());
            }
        }
        if (!symbols.equals(new HashSet<>(request.targetSymbols()))) {
            throw new IllegalArgumentException("Eligibility results must exactly cover request targetSymbols.");
        }
        return copied.stream().sorted(Comparator.comparing(result -> result.input().symbol())).toList();
    }

    private static List<DailyPriceHistory> validateInputHistories(
            StockCandidateEvaluationRequest request,
            List<DailyPriceHistory> histories
    ) {
        List<DailyPriceHistory> copied = List.copyOf(Objects.requireNonNull(
                histories, "inputHistories must not be null."
        ));
        Set<String> targets = new HashSet<>(request.targetSymbols());
        Set<String> symbols = new HashSet<>();
        for (DailyPriceHistory history : copied) {
            if (!targets.contains(history.symbol())) {
                throw new IllegalArgumentException("Input history symbol must belong to request targetSymbols: "
                        + history.symbol());
            }
            if (!symbols.add(history.symbol())) {
                throw new IllegalArgumentException("Duplicate input history symbol: " + history.symbol());
            }
        }
        return copied.stream().sorted(Comparator.comparing(DailyPriceHistory::symbol)).toList();
    }
}
