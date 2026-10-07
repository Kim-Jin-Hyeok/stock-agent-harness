package com.stock.strategy.universe.eligibility.restriction.kis.screening;

import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningResult;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningResult;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.KisStockMarketWarningObservationPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningObservationResult;

import java.util.Objects;

public class KisStockRestrictionScreeningPolicy {
    public static final String SCREENING_VERSION = "KIS_STOCK_RESTRICTION_SCREENING_V1";

    public KisStockRestrictionScreeningResult evaluate(
            KisStockBasicInfoRestrictionScreeningResult basicInfoScreening,
            KisStockMarketWarningObservationResult marketWarningObservation
    ) {
        var reasons = KisStockRestrictionScreeningReasonCode.fromInputs(basicInfoScreening, marketWarningObservation);
        return new KisStockRestrictionScreeningResult(basicInfoScreening, marketWarningObservation,
                KisStockRestrictionScreeningStatus.fromReasonCodes(reasons), reasons, SCREENING_VERSION);
    }

    public static void requireSupportedInputs(
            KisStockBasicInfoRestrictionScreeningResult basicInfoScreening,
            KisStockMarketWarningObservationResult marketWarningObservation
    ) {
        Objects.requireNonNull(basicInfoScreening, "basicInfoScreening must not be null.");
        Objects.requireNonNull(marketWarningObservation, "marketWarningObservation must not be null.");
        // Freeze the paired contracts; preserving V1 evidence does not imply accepting it here.
        if (!"KIS_STOCK_BASIC_INFO_RESTRICTION_SCREENING_V2".equals(basicInfoScreening.screeningVersion())
                || !"KIS_STOCK_MARKET_WARNING_OBSERVATION_V1".equals(marketWarningObservation.observationVersion())
                || !"277ec0eb7a9b7f63b6807829286c80f36649dad2".equals(marketWarningObservation.sourceRevision())) {
            throw new IllegalArgumentException("Combined restriction screening requires the supported basic info V2 and market warning V1 contracts.");
        }
        KisStockMarketWarningObservationPolicy.requireSupportedSource(marketWarningObservation.source());
    }
}
