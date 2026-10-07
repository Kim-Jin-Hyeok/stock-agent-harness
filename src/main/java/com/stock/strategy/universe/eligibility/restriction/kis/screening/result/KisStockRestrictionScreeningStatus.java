package com.stock.strategy.universe.eligibility.restriction.kis.screening.result;

import java.util.List;
import java.util.Objects;

public enum KisStockRestrictionScreeningStatus {
    EXCLUSION_SIGNAL_OBSERVED,
    REVIEW_REQUIRED,
    NO_EXCLUSION_SIGNAL_OBSERVED;

    public static KisStockRestrictionScreeningStatus fromReasonCodes(List<KisStockRestrictionScreeningReasonCode> reasons) {
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons must not be null."));
        if (reasons.stream().anyMatch(KisStockRestrictionScreeningReasonCode::isConnectionFailure)) {
            return REVIEW_REQUIRED;
        }
        if (reasons.stream().anyMatch(KisStockRestrictionScreeningReasonCode::isExclusionSignal)) {
            return EXCLUSION_SIGNAL_OBSERVED;
        }
        return reasons.isEmpty() ? NO_EXCLUSION_SIGNAL_OBSERVED : REVIEW_REQUIRED;
    }
}
