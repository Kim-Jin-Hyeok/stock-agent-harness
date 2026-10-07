package com.stock.strategy.universe.eligibility.restriction.kis.precheck.result;

import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus;

import java.util.Objects;

public enum KisStockRestrictionPrecheckStatus {
    CLEAR,
    BLOCKED;

    public static KisStockRestrictionPrecheckStatus fromInputs(
            KisStockRestrictionScreeningStatus restrictionStatus,
            KisStockRestrictionFreshnessStatus freshnessStatus
    ) {
        Objects.requireNonNull(restrictionStatus, "restrictionStatus must not be null.");
        Objects.requireNonNull(freshnessStatus, "freshnessStatus must not be null.");
        return restrictionStatus == KisStockRestrictionScreeningStatus.NO_EXCLUSION_SIGNAL_OBSERVED
                && freshnessStatus == KisStockRestrictionFreshnessStatus.FRESH ? CLEAR : BLOCKED;
    }
}
