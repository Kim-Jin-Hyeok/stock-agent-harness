package com.stock.strategy.universe.eligibility.restriction.kis.precheck.result;

import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessResult;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.KisStockRestrictionPrecheckPolicy;

import java.util.Objects;

public record KisStockRestrictionPrecheckResult(
        KisStockRestrictionFreshnessResult freshnessResult,
        KisStockRestrictionPrecheckStatus status,
        String precheckVersion
) {
    public KisStockRestrictionPrecheckResult {
        Objects.requireNonNull(freshnessResult, "freshnessResult must not be null.");
        Objects.requireNonNull(status, "status must not be null.");
        if (!KisStockRestrictionPrecheckPolicy.PRECHECK_VERSION.equals(precheckVersion)) {
            throw new IllegalArgumentException("precheckVersion must be the supported restriction precheck version.");
        }
        if (status != KisStockRestrictionPrecheckStatus.fromInputs(
                freshnessResult.analysisResult().restrictionScreeningResult().status(), freshnessResult.status())) {
            throw new IllegalArgumentException("Precheck status must agree with the original restriction and freshness statuses.");
        }
    }

    @Override
    public String toString() {
        var analysis = freshnessResult.analysisResult();
        var restriction = analysis.restrictionScreeningResult();
        return "KisStockRestrictionPrecheckResult[observationId=" + analysis.basicInfoAnalysis().observationId()
                + ", requestedSymbol=" + analysis.basicInfoAnalysis().response().requestedSymbol()
                + ", evaluatedAt=" + freshnessResult.request().evaluatedAt()
                + ", restrictionStatus=" + restriction.status() + ", restrictionReasons=" + restriction.reasonCodes()
                + ", freshnessStatus=" + freshnessResult.status() + ", freshnessReasons=" + freshnessResult.reasonCodes()
                + ", status=" + status + ", precheckVersion=" + precheckVersion + "]";
    }
}
