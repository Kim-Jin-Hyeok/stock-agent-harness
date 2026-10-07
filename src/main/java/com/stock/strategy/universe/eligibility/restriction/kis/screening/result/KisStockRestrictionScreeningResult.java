package com.stock.strategy.universe.eligibility.restriction.kis.screening.result;

import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningResult;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.KisStockRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningObservationResult;

import java.util.List;
import java.util.Objects;

public record KisStockRestrictionScreeningResult(
        KisStockBasicInfoRestrictionScreeningResult basicInfoScreening,
        KisStockMarketWarningObservationResult marketWarningObservation,
        KisStockRestrictionScreeningStatus status,
        List<KisStockRestrictionScreeningReasonCode> reasonCodes,
        String screeningVersion
) {
    public KisStockRestrictionScreeningResult {
        KisStockRestrictionScreeningPolicy.requireSupportedInputs(basicInfoScreening, marketWarningObservation);
        Objects.requireNonNull(status, "status must not be null.");
        reasonCodes = List.copyOf(Objects.requireNonNull(reasonCodes, "reasonCodes must not be null."));
        if (!KisStockRestrictionScreeningPolicy.SCREENING_VERSION.equals(screeningVersion)) {
            throw new IllegalArgumentException("screeningVersion must be the supported combined restriction screening version.");
        }
        if (!reasonCodes.equals(KisStockRestrictionScreeningReasonCode.fromInputs(basicInfoScreening, marketWarningObservation))) {
            throw new IllegalArgumentException("reasonCodes must preserve all connection diagnostics and observed screening reasons in their defined order.");
        }
        if (status != KisStockRestrictionScreeningStatus.fromReasonCodes(reasonCodes)) {
            throw new IllegalArgumentException("status must agree with both original inputs and connection diagnostics.");
        }
    }
}
