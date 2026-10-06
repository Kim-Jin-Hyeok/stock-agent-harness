package com.stock.strategy.universe.eligibility.restriction.kis.result;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.classification.kiskrx.result.KisKrxStockTypeResolutionResult;

import java.util.Objects;

public record KisStockTradingRestrictionResult(
        KisKrxStockTypeResolutionResult typeResolution,
        KisStockTradingFlagStatus suspensionStatus,
        KisStockTradingFlagStatus liquidationStatus,
        KisStockTradingFlagStatus spacStatus,
        KisStockTradingFlagStatus managementStatus,
        KisStockTradingFlagStatus investmentCautionStatus,
        String restrictionVersion,
        String sourceRevision
) {
    public KisStockTradingRestrictionResult {
        Objects.requireNonNull(typeResolution, "typeResolution must not be null.");
        Objects.requireNonNull(suspensionStatus, "suspensionStatus must not be null.");
        Objects.requireNonNull(liquidationStatus, "liquidationStatus must not be null.");
        Objects.requireNonNull(spacStatus, "spacStatus must not be null.");
        Objects.requireNonNull(managementStatus, "managementStatus must not be null.");
        Objects.requireNonNull(investmentCautionStatus, "investmentCautionStatus must not be null.");
        if (restrictionVersion == null || restrictionVersion.isBlank()) {
            throw new IllegalArgumentException("restrictionVersion must not be blank.");
        }
        if (sourceRevision == null || !sourceRevision.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("sourceRevision must be a full lowercase Git commit hash.");
        }
        var raw = typeResolution.kisClassification().rawRecord();
        if ((typeResolution.kisClassification().market() == KisStockMasterMarket.KOSPI)
                != (raw.rawInvestmentCaution() == null)) {
            throw new IllegalArgumentException("Investment caution field presence must match the source market layout.");
        }
        requireConsistent(raw.rawSuspension(), suspensionStatus, "suspensionStatus");
        requireConsistent(raw.rawLiquidation(), liquidationStatus, "liquidationStatus");
        requireConsistent(raw.rawSpac(), spacStatus, "spacStatus");
        requireConsistent(raw.rawManagement(), managementStatus, "managementStatus");
        requireConsistent(raw.rawInvestmentCaution(), investmentCautionStatus, "investmentCautionStatus");
    }

    private static void requireConsistent(String rawValue, KisStockTradingFlagStatus status, String field) {
        boolean consistent = switch (status) {
            case Y_OBSERVED -> "Y".equals(rawValue);
            case N_OBSERVED -> "N".equals(rawValue);
            case VALUE_UNVERIFIED -> rawValue != null && !"Y".equals(rawValue) && !"N".equals(rawValue);
            case FIELD_NOT_PROVIDED -> rawValue == null;
        };
        if (!consistent) {
            throw new IllegalArgumentException(field + " must agree with its original KIS field value.");
        }
    }
}
