package com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result;

import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.result.KisStockBasicInfoRestrictionObservationResult;

import java.util.List;
import java.util.Objects;

public record KisStockBasicInfoRestrictionScreeningResult(
        KisStockBasicInfoRestrictionObservationResult observation,
        KisStockBasicInfoRestrictionScreeningStatus status,
        List<KisStockBasicInfoRestrictionScreeningReasonCode> reasonCodes,
        String screeningVersion
) {
    public KisStockBasicInfoRestrictionScreeningResult {
        Objects.requireNonNull(observation, "observation must not be null.");
        Objects.requireNonNull(status, "status must not be null.");
        reasonCodes = List.copyOf(Objects.requireNonNull(reasonCodes, "reasonCodes must not be null."));
        if (screeningVersion == null || screeningVersion.isBlank()) {
            throw new IllegalArgumentException("screeningVersion must not be blank.");
        }
        if (!reasonCodes.equals(KisStockBasicInfoRestrictionScreeningReasonCode.fromObservation(observation))) {
            throw new IllegalArgumentException("reasonCodes must preserve all observed reasons in their defined order.");
        }
        if (status != KisStockBasicInfoRestrictionScreeningStatus.fromObservation(observation)) {
            throw new IllegalArgumentException("status must agree with the original observation and match outcome.");
        }
    }
}
