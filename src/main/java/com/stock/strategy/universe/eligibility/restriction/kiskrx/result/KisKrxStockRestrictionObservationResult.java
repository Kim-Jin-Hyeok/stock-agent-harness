package com.stock.strategy.universe.eligibility.restriction.kiskrx.result;

import com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingRestrictionResult;
import com.stock.strategy.universe.eligibility.restriction.krx.result.KrxStockSectionRestrictionResult;

import java.util.Objects;

public record KisKrxStockRestrictionObservationResult(
        KisStockTradingRestrictionResult kisObservation,
        KrxStockSectionRestrictionResult krxObservation,
        String observationVersion
) {
    public KisKrxStockRestrictionObservationResult {
        Objects.requireNonNull(kisObservation, "kisObservation must not be null.");
        Objects.requireNonNull(krxObservation, "krxObservation must not be null.");
        if (observationVersion == null || observationVersion.isBlank()) {
            throw new IllegalArgumentException("observationVersion must not be blank.");
        }
        // Full value equality keeps JSON round trips valid without accepting symbol-only matches.
        if (!kisObservation.typeResolution().equals(krxObservation.typeResolution())) {
            throw new IllegalArgumentException("KIS and KRX observations must share the same type resolution.");
        }
    }
}
