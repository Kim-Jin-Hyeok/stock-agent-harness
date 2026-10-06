package com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.result;

import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.result.KisStockBasicInfoTypeResolutionResult;
import com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus;

import java.util.Objects;

public record KisStockBasicInfoRestrictionObservationResult(
        KisStockBasicInfoTypeResolutionResult typeResolution,
        KisStockTradingFlagStatus masterSuspensionStatus,
        KisStockTradingFlagStatus masterLiquidationStatus,
        KisStockTradingFlagStatus masterSpacStatus,
        KisStockTradingFlagStatus masterManagementStatus,
        KisStockTradingFlagStatus masterInvestmentCautionStatus,
        KisStockTradingFlagStatus basicInfoSuspensionStatus,
        KisStockTradingFlagStatus basicInfoManagementStatus,
        String observationVersion
) {
    public KisStockBasicInfoRestrictionObservationResult {
        Objects.requireNonNull(typeResolution, "typeResolution must not be null.");
        if (observationVersion == null || observationVersion.isBlank()) {
            throw new IllegalArgumentException("observationVersion must not be blank.");
        }
        var master = typeResolution.masterClassification();
        if (master == null) {
            if (masterSuspensionStatus != null || masterLiquidationStatus != null || masterSpacStatus != null
                    || masterManagementStatus != null || masterInvestmentCautionStatus != null) {
                throw new IllegalArgumentException("Master observations must be absent when the requested master record is missing.");
            }
        } else {
            var raw = master.rawRecord();
            requireConsistent(raw.rawSuspension(), masterSuspensionStatus, "masterSuspensionStatus");
            requireConsistent(raw.rawLiquidation(), masterLiquidationStatus, "masterLiquidationStatus");
            requireConsistent(raw.rawSpac(), masterSpacStatus, "masterSpacStatus");
            requireConsistent(raw.rawManagement(), masterManagementStatus, "masterManagementStatus");
            requireConsistent(raw.rawInvestmentCaution(), masterInvestmentCautionStatus, "masterInvestmentCautionStatus");
        }
        var api = typeResolution.basicInfoClassification();
        if (api == null) {
            if (basicInfoSuspensionStatus != null || basicInfoManagementStatus != null) {
                throw new IllegalArgumentException("Basic info observations require a successful standard code and market match.");
            }
        } else {
            var raw = api.parseResult().rawRecord();
            requireConsistent(raw.rawSuspension(), basicInfoSuspensionStatus, "basicInfoSuspensionStatus");
            requireConsistent(raw.rawManagement(), basicInfoManagementStatus, "basicInfoManagementStatus");
        }
    }

    private static void requireConsistent(String rawValue, KisStockTradingFlagStatus status, String field) {
        if (status != KisStockTradingFlagStatus.fromRawValue(rawValue)) {
            throw new IllegalArgumentException(field + " must agree with its original field value.");
        }
    }
}
