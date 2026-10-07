package com.stock.strategy.universe.eligibility.restriction.kis.freshness;

import com.stock.market.stock.basicinfo.observation.analysis.restriction.result.KisStockRestrictionAnalysisResult;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.request.KisStockRestrictionFreshnessRequest;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessReasonCode;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessResult;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus;

public class KisStockRestrictionFreshnessPolicy {
    public static final String FRESHNESS_VERSION = "KIS_STOCK_RESTRICTION_FRESHNESS_V1";

    public KisStockRestrictionFreshnessResult evaluate(
            KisStockRestrictionFreshnessRequest request,
            KisStockRestrictionAnalysisResult analysisResult
    ) {
        var reasons = KisStockRestrictionFreshnessReasonCode.fromInputs(request, analysisResult);
        return new KisStockRestrictionFreshnessResult(request, analysisResult,
                KisStockRestrictionFreshnessStatus.fromReasonCodes(reasons), reasons, FRESHNESS_VERSION);
    }
}
