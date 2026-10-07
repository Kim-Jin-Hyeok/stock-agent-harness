package com.stock.strategy.universe.eligibility.restriction.kis.freshness.result;

import java.util.List;
import java.util.Objects;

public enum KisStockRestrictionFreshnessStatus {
    FRESH,
    EXPIRED,
    TIME_UNVERIFIED;

    public static KisStockRestrictionFreshnessStatus fromReasonCodes(List<KisStockRestrictionFreshnessReasonCode> reasons) {
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons must not be null."));
        if (reasons.stream().anyMatch(KisStockRestrictionFreshnessReasonCode::isTimeUnverified)) {
            return TIME_UNVERIFIED;
        }
        return reasons.isEmpty() ? FRESH : EXPIRED;
    }
}
