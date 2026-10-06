package com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening;

import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.result.KisStockBasicInfoRestrictionObservationResult;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningResult;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningStatus;

import java.util.Objects;

public class KisStockBasicInfoRestrictionScreeningPolicy {
    public static final String SCREENING_VERSION = "KIS_STOCK_BASIC_INFO_RESTRICTION_SCREENING_V1";

    public KisStockBasicInfoRestrictionScreeningResult evaluate(KisStockBasicInfoRestrictionObservationResult observation) {
        Objects.requireNonNull(observation, "observation must not be null.");
        return new KisStockBasicInfoRestrictionScreeningResult(observation,
                KisStockBasicInfoRestrictionScreeningStatus.fromObservation(observation),
                KisStockBasicInfoRestrictionScreeningReasonCode.fromObservation(observation), SCREENING_VERSION);
    }
}
