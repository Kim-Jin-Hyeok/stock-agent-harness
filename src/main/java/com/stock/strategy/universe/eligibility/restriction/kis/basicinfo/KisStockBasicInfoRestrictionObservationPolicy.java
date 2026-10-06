package com.stock.strategy.universe.eligibility.restriction.kis.basicinfo;

import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.result.KisStockBasicInfoTypeResolutionResult;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.result.KisStockBasicInfoRestrictionObservationResult;

import java.util.Objects;

import static com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus.fromRawValue;

public class KisStockBasicInfoRestrictionObservationPolicy {
    public static final String OBSERVATION_VERSION = "KIS_STOCK_BASIC_INFO_RESTRICTION_OBSERVATION_V1";

    public KisStockBasicInfoRestrictionObservationResult evaluate(KisStockBasicInfoTypeResolutionResult typeResolution) {
        Objects.requireNonNull(typeResolution, "typeResolution must not be null.");
        var master = typeResolution.masterClassification();
        if (master == null) {
            return new KisStockBasicInfoRestrictionObservationResult(typeResolution,
                    null, null, null, null, null, null, null, OBSERVATION_VERSION);
        }
        var raw = master.rawRecord();
        // The type result retains an API classification only after a successful comparison.
        var api = typeResolution.basicInfoClassification();
        var apiRaw = api == null ? null : api.parseResult().rawRecord();
        return new KisStockBasicInfoRestrictionObservationResult(typeResolution,
                fromRawValue(raw.rawSuspension()), fromRawValue(raw.rawLiquidation()), fromRawValue(raw.rawSpac()),
                fromRawValue(raw.rawManagement()), fromRawValue(raw.rawInvestmentCaution()),
                apiRaw == null ? null : fromRawValue(apiRaw.rawSuspension()),
                apiRaw == null ? null : fromRawValue(apiRaw.rawManagement()), OBSERVATION_VERSION);
    }
}
