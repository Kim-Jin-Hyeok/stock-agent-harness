package com.stock.strategy.universe.eligibility.restriction.kis.precheck;

import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessResult;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.result.KisStockRestrictionPrecheckResult;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.result.KisStockRestrictionPrecheckStatus;

import java.util.Objects;

public class KisStockRestrictionPrecheckPolicy {
    public static final String PRECHECK_VERSION = "KIS_STOCK_RESTRICTION_PRECHECK_V1";

    public KisStockRestrictionPrecheckResult evaluate(KisStockRestrictionFreshnessResult freshnessResult) {
        Objects.requireNonNull(freshnessResult, "freshnessResult must not be null.");
        var restrictionStatus = freshnessResult.analysisResult().restrictionScreeningResult().status();
        return new KisStockRestrictionPrecheckResult(freshnessResult,
                KisStockRestrictionPrecheckStatus.fromInputs(restrictionStatus, freshnessResult.status()), PRECHECK_VERSION);
    }
}
