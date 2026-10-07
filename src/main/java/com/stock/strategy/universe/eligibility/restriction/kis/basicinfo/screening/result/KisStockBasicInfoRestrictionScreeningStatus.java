package com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result;

import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.result.KisStockBasicInfoRestrictionObservationResult;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.KisStockBasicInfoRestrictionScreeningPolicy;

public enum KisStockBasicInfoRestrictionScreeningStatus {
    EXCLUSION_SIGNAL_OBSERVED,
    REVIEW_REQUIRED,
    NO_EXCLUSION_SIGNAL_OBSERVED;

    public static KisStockBasicInfoRestrictionScreeningStatus fromObservation(
            KisStockBasicInfoRestrictionObservationResult observation
    ) {
        return fromObservation(observation, KisStockBasicInfoRestrictionScreeningPolicy.SCREENING_VERSION);
    }

    public static KisStockBasicInfoRestrictionScreeningStatus fromObservation(
            KisStockBasicInfoRestrictionObservationResult observation, String screeningVersion
    ) {
        var reasons = KisStockBasicInfoRestrictionScreeningReasonCode.fromObservation(observation, screeningVersion);
        if (reasons.contains(KisStockBasicInfoRestrictionScreeningReasonCode.STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED)) {
            return REVIEW_REQUIRED;
        }
        if (reasons.stream().anyMatch(KisStockBasicInfoRestrictionScreeningReasonCode::isExclusionSignal)) {
            return EXCLUSION_SIGNAL_OBSERVED;
        }
        boolean cautionNotApplicable = reasons.contains(KisStockBasicInfoRestrictionScreeningReasonCode.MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE);
        boolean reviewRequired = reasons.stream().anyMatch(reason ->
                reason != KisStockBasicInfoRestrictionScreeningReasonCode.MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE
                        && (reason != KisStockBasicInfoRestrictionScreeningReasonCode.MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED
                        || !cautionNotApplicable));
        // Explanations remain even without a blocker; no signal is not trading permission.
        return reviewRequired ? REVIEW_REQUIRED : NO_EXCLUSION_SIGNAL_OBSERVED;
    }
}
