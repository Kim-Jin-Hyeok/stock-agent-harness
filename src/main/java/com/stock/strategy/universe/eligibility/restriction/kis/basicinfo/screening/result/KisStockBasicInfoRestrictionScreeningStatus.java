package com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result;

import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.result.KisStockBasicInfoRestrictionObservationResult;

public enum KisStockBasicInfoRestrictionScreeningStatus {
    EXCLUSION_SIGNAL_OBSERVED,
    REVIEW_REQUIRED,
    NO_EXCLUSION_SIGNAL_OBSERVED;

    public static KisStockBasicInfoRestrictionScreeningStatus fromObservation(
            KisStockBasicInfoRestrictionObservationResult observation
    ) {
        var reasons = KisStockBasicInfoRestrictionScreeningReasonCode.fromObservation(observation);
        if (reasons.contains(KisStockBasicInfoRestrictionScreeningReasonCode.STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED)) {
            return REVIEW_REQUIRED;
        }
        if (reasons.stream().anyMatch(KisStockBasicInfoRestrictionScreeningReasonCode::isExclusionSignal)) {
            return EXCLUSION_SIGNAL_OBSERVED;
        }
        // No signal is not an eligibility, freshness, listing or trading-permission approval.
        return reasons.isEmpty() ? NO_EXCLUSION_SIGNAL_OBSERVED : REVIEW_REQUIRED;
    }
}
