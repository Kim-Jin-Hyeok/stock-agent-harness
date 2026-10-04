package com.stock.strategy.universe.candidate.evaluation.request;

import com.stock.strategy.universe.eligibility.request.StockEligibilityRequest;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;

import java.util.List;
import java.util.Objects;

public record StockCandidateEvaluationRequest(
        StockEligibilityRequest eligibilityRequest,
        DailyTradingValueSelectionEvaluationRequest liquidityRequest
) {
    public StockCandidateEvaluationRequest {
        Objects.requireNonNull(eligibilityRequest, "eligibilityRequest must not be null.");
        Objects.requireNonNull(liquidityRequest, "liquidityRequest must not be null.");
        if (!eligibilityRequest.selectionAsOfDate().equals(liquidityRequest.selectionAsOfDate())) {
            throw new IllegalArgumentException("Eligibility and liquidity selectionAsOfDate must match.");
        }
    }

    public List<String> targetSymbols() {
        return liquidityRequest.targetSymbols();
    }

    public DailyTradingValueSelectionEvaluationRequest liquidityRequestFor(List<String> eligibleSymbols) {
        Objects.requireNonNull(eligibleSymbols, "eligibleSymbols must not be null.");
        if (!targetSymbols().containsAll(eligibleSymbols)) {
            throw new IllegalArgumentException("eligibleSymbols must belong to request targetSymbols.");
        }
        return new DailyTradingValueSelectionEvaluationRequest(
                eligibleSymbols, liquidityRequest.selectionAsOfDate(), liquidityRequest.requiredTradingDates(),
                liquidityRequest.expectedVenueScope(), liquidityRequest.minimumAverageTradingValueKrw(),
                liquidityRequest.maxCandidateCount()
        );
    }
}
