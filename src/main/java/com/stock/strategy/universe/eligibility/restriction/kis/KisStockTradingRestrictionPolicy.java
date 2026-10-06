package com.stock.strategy.universe.eligibility.restriction.kis;

import com.stock.strategy.universe.eligibility.classification.kiskrx.result.KisKrxStockTypeResolutionResult;
import com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingRestrictionResult;

import java.util.Objects;

public class KisStockTradingRestrictionPolicy {
    public static final String RESTRICTION_VERSION = "KIS_STOCK_TRADING_FLAG_OBSERVATION_V1";
    public static final String SOURCE_REVISION = "277ec0eb7a9b7f63b6807829286c80f36649dad2";

    public KisStockTradingRestrictionResult evaluate(KisKrxStockTypeResolutionResult typeResolution) {
        Objects.requireNonNull(typeResolution, "typeResolution must not be null.");
        var raw = typeResolution.kisClassification().rawRecord();
        return new KisStockTradingRestrictionResult(typeResolution,
                observe(raw.rawSuspension()), observe(raw.rawLiquidation()), RESTRICTION_VERSION, SOURCE_REVISION);
    }

    private static KisStockTradingFlagStatus observe(String rawValue) {
        // These states describe literal field observations, not current or historical trading permission.
        return switch (rawValue) {
            case "Y" -> KisStockTradingFlagStatus.Y_OBSERVED;
            case "N" -> KisStockTradingFlagStatus.N_OBSERVED;
            default -> KisStockTradingFlagStatus.VALUE_UNVERIFIED;
        };
    }
}
