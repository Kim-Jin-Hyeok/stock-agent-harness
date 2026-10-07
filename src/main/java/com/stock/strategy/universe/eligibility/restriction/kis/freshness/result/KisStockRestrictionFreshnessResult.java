package com.stock.strategy.universe.eligibility.restriction.kis.freshness.result;

import com.stock.market.stock.basicinfo.observation.analysis.restriction.result.KisStockRestrictionAnalysisResult;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.KisStockRestrictionFreshnessPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.request.KisStockRestrictionFreshnessRequest;

import java.util.List;
import java.util.Objects;

public record KisStockRestrictionFreshnessResult(
        KisStockRestrictionFreshnessRequest request,
        KisStockRestrictionAnalysisResult analysisResult,
        KisStockRestrictionFreshnessStatus status,
        List<KisStockRestrictionFreshnessReasonCode> reasonCodes,
        String freshnessVersion
) {
    public KisStockRestrictionFreshnessResult {
        Objects.requireNonNull(request, "request must not be null.");
        Objects.requireNonNull(analysisResult, "analysisResult must not be null.");
        Objects.requireNonNull(status, "status must not be null.");
        reasonCodes = List.copyOf(Objects.requireNonNull(reasonCodes, "reasonCodes must not be null."));
        if (!KisStockRestrictionFreshnessPolicy.FRESHNESS_VERSION.equals(freshnessVersion)) {
            throw new IllegalArgumentException("freshnessVersion must be the supported restriction freshness version.");
        }
        if (!reasonCodes.equals(KisStockRestrictionFreshnessReasonCode.fromInputs(request, analysisResult))) {
            throw new IllegalArgumentException("Freshness reasons must preserve all input timing and source diagnostics in their defined order.");
        }
        if (status != KisStockRestrictionFreshnessStatus.fromReasonCodes(reasonCodes)) {
            throw new IllegalArgumentException("Freshness status must agree with its input timing and source diagnostics.");
        }
    }

    @Override
    public String toString() {
        return "KisStockRestrictionFreshnessResult[observationId=" + analysisResult.basicInfoAnalysis().observationId()
                + ", requestedSymbol=" + analysisResult.basicInfoAnalysis().response().requestedSymbol()
                + ", evaluatedAt=" + request.evaluatedAt() + ", restrictionStatus=" + analysisResult.restrictionScreeningResult().status()
                + ", status=" + status + ", reasonCodes=" + reasonCodes + ", freshnessVersion=" + freshnessVersion + "]";
    }
}
