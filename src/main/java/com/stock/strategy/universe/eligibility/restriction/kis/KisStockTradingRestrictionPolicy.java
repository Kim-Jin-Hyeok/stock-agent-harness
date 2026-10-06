package com.stock.strategy.universe.eligibility.restriction.kis;

import com.stock.strategy.universe.eligibility.classification.kiskrx.result.KisKrxStockTypeResolutionResult;
import com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingRestrictionResult;

import java.util.Objects;

import static com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus.fromRawValue;

public class KisStockTradingRestrictionPolicy {
    public static final String RESTRICTION_VERSION = "KIS_STOCK_TRADING_FLAG_OBSERVATION_V2";
    public static final String SOURCE_REVISION = "277ec0eb7a9b7f63b6807829286c80f36649dad2";

    public KisStockTradingRestrictionResult evaluate(KisKrxStockTypeResolutionResult typeResolution) {
        Objects.requireNonNull(typeResolution, "typeResolution must not be null.");
        var raw = typeResolution.kisClassification().rawRecord();
        return new KisStockTradingRestrictionResult(typeResolution,
                fromRawValue(raw.rawSuspension()), fromRawValue(raw.rawLiquidation()), fromRawValue(raw.rawSpac()),
                fromRawValue(raw.rawManagement()), fromRawValue(raw.rawInvestmentCaution()), RESTRICTION_VERSION, SOURCE_REVISION);
    }
}
