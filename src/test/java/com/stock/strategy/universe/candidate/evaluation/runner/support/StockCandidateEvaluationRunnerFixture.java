package com.stock.strategy.universe.candidate.evaluation.runner.support;

import com.stock.strategy.universe.candidate.evaluation.request.StockCandidateEvaluationRequest;
import com.stock.strategy.universe.candidate.evaluation.runner.config.StockCandidateEvaluationProperties;
import com.stock.strategy.universe.candidate.evaluation.snapshot.StockCandidateEvaluationSnapshot;
import com.stock.strategy.universe.eligibility.input.StockEligibilityInput;

import java.util.List;

public final class StockCandidateEvaluationRunnerFixture {
    private StockCandidateEvaluationRunnerFixture() {}

    public static StockCandidateEvaluationProperties disabledProperties() {
        return new StockCandidateEvaluationProperties(false, null, null, null, null, null,
                null, null, null, null, null);
    }

    public static StockCandidateEvaluationProperties properties(StockCandidateEvaluationSnapshot snapshot) {
        return properties(snapshot.evaluationResult().request(), snapshot.evaluationResult().eligibilityResults().stream()
                .map(value -> value.input()).toList());
    }

    public static StockCandidateEvaluationProperties properties(
            StockCandidateEvaluationRequest request,
            List<StockEligibilityInput> inputs
    ) {
        var eligibility = request.eligibilityRequest();
        var liquidity = request.liquidityRequest();
        return new StockCandidateEvaluationProperties(true, request.targetSymbols(), eligibility.selectionAsOfDate(),
                eligibility.selectionCutoffAt(), eligibility.eligibleMarkets(), eligibility.eligibleSecurityTypes(),
                liquidity.requiredTradingDates(), liquidity.expectedVenueScope(), liquidity.minimumAverageTradingValueKrw(),
                liquidity.maxCandidateCount(), inputs);
    }
}
