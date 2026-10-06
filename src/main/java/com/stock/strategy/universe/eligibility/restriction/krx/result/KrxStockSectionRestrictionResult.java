package com.stock.strategy.universe.eligibility.restriction.krx.result;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.classification.kiskrx.result.KisKrxStockTypeResolutionResult;

import java.util.Objects;

public record KrxStockSectionRestrictionResult(
        KisKrxStockTypeResolutionResult typeResolution,
        KrxStockSectionRestrictionReasonCode reasonCode,
        String restrictionVersion,
        String sourceReference,
        String sourceSha256
) {
    public KrxStockSectionRestrictionResult {
        Objects.requireNonNull(typeResolution, "typeResolution must not be null.");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null.");
        if (restrictionVersion == null || restrictionVersion.isBlank()) {
            throw new IllegalArgumentException("restrictionVersion must not be blank.");
        }
        if (sourceReference == null || sourceReference.isBlank()) {
            throw new IllegalArgumentException("sourceReference must not be blank.");
        }
        if (sourceSha256 == null || !sourceSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sourceSha256 must be a full lowercase SHA-256.");
        }
        if ((reasonCode == KrxStockSectionRestrictionReasonCode.IDENTITY_MATCH_UNVERIFIED)
                != (typeResolution.identityMatch() == null)) {
            throw new IllegalArgumentException("Section interpretation requires an exact identity match.");
        }
        if (reasonCode != KrxStockSectionRestrictionReasonCode.IDENTITY_MATCH_UNVERIFIED
                && reasonCode != KrxStockSectionRestrictionReasonCode.SECTION_UNVERIFIED
                && typeResolution.identityMatch().requestMarket() != KisStockMasterMarket.KOSDAQ) {
            throw new IllegalArgumentException("Observed section interpretation is limited to KOSDAQ.");
        }
    }
}
