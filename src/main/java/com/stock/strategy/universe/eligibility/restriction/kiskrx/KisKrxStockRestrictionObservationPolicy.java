package com.stock.strategy.universe.eligibility.restriction.kiskrx;

import com.stock.strategy.universe.eligibility.classification.kiskrx.result.KisKrxStockTypeResolutionResult;
import com.stock.strategy.universe.eligibility.restriction.kis.KisStockTradingRestrictionPolicy;
import com.stock.strategy.universe.eligibility.restriction.kiskrx.result.KisKrxStockRestrictionObservationResult;
import com.stock.strategy.universe.eligibility.restriction.krx.KrxStockSectionRestrictionPolicy;

import java.util.Objects;

public class KisKrxStockRestrictionObservationPolicy {
    public static final String OBSERVATION_VERSION = "KIS_KRX_STOCK_RESTRICTION_OBSERVATION_V1";
    private final KisStockTradingRestrictionPolicy kisPolicy;
    private final KrxStockSectionRestrictionPolicy krxPolicy;

    public KisKrxStockRestrictionObservationPolicy(
            KisStockTradingRestrictionPolicy kisPolicy,
            KrxStockSectionRestrictionPolicy krxPolicy
    ) {
        this.kisPolicy = Objects.requireNonNull(kisPolicy, "kisPolicy must not be null.");
        this.krxPolicy = Objects.requireNonNull(krxPolicy, "krxPolicy must not be null.");
    }

    public KisKrxStockRestrictionObservationResult evaluate(KisKrxStockTypeResolutionResult typeResolution) {
        Objects.requireNonNull(typeResolution, "typeResolution must not be null.");
        var kis = Objects.requireNonNull(kisPolicy.evaluate(typeResolution), "KIS observation must not be null.");
        var krx = Objects.requireNonNull(krxPolicy.evaluate(typeResolution), "KRX observation must not be null.");
        if (!typeResolution.equals(kis.typeResolution()) || !typeResolution.equals(krx.typeResolution())) {
            throw new IllegalArgumentException("Source observations must preserve the requested type resolution.");
        }
        return new KisKrxStockRestrictionObservationResult(kis, krx, OBSERVATION_VERSION);
    }
}
