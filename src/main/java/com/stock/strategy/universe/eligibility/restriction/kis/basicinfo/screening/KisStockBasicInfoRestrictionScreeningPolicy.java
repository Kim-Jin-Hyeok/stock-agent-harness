package com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening;

import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.result.KisStockBasicInfoRestrictionObservationResult;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningResult;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningStatus;

import java.util.Objects;

public class KisStockBasicInfoRestrictionScreeningPolicy {
    public static final String SCREENING_VERSION_V1 = "KIS_STOCK_BASIC_INFO_RESTRICTION_SCREENING_V1";
    public static final String SCREENING_VERSION_V2 = "KIS_STOCK_BASIC_INFO_RESTRICTION_SCREENING_V2";
    public static final String SCREENING_VERSION = SCREENING_VERSION_V2;

    public KisStockBasicInfoRestrictionScreeningResult evaluate(KisStockBasicInfoRestrictionObservationResult observation) {
        return evaluateVersion(observation, SCREENING_VERSION);
    }

    public KisStockBasicInfoRestrictionScreeningResult evaluate(
            KisStockBasicInfoRestrictionObservationResult observation, String screeningVersion
    ) {
        return evaluateVersion(observation, screeningVersion);
    }

    public static void requireSupportedVersion(String screeningVersion) {
        if (screeningVersion == null || screeningVersion.isBlank()) {
            throw new IllegalArgumentException("screeningVersion must not be blank.");
        }
        if (!SCREENING_VERSION_V1.equals(screeningVersion) && !SCREENING_VERSION_V2.equals(screeningVersion)) {
            throw new IllegalArgumentException("screeningVersion must be a supported restriction screening version.");
        }
    }

    private KisStockBasicInfoRestrictionScreeningResult evaluateVersion(
            KisStockBasicInfoRestrictionObservationResult observation, String screeningVersion
    ) {
        Objects.requireNonNull(observation, "observation must not be null.");
        return new KisStockBasicInfoRestrictionScreeningResult(observation,
                KisStockBasicInfoRestrictionScreeningStatus.fromObservation(observation, screeningVersion),
                KisStockBasicInfoRestrictionScreeningReasonCode.fromObservation(observation, screeningVersion), screeningVersion);
    }
}
