package com.stock.strategy.universe.eligibility.restriction.kis.precheck.support;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.KisStockRestrictionFreshnessPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessResult;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus;

import java.util.Map;

import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.EVALUATED_AT;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.MAX_BASIC_INFO_AGE;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.MAX_MASTER_AGE;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.analysis;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.request;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.withTimes;

public final class KisStockRestrictionPrecheckFixture {
    private KisStockRestrictionPrecheckFixture() {
    }

    public static KisStockRestrictionFreshnessResult freshness(
            KisStockMasterMarket market,
            KisStockRestrictionScreeningStatus restrictionStatus,
            KisStockRestrictionFreshnessStatus freshnessStatus
    ) {
        String warningCode = switch (restrictionStatus) {
            case NO_EXCLUSION_SIGNAL_OBSERVED -> "00";
            case EXCLUSION_SIGNAL_OBSERVED -> "02";
            case REVIEW_REQUIRED -> "99";
        };
        var original = analysis(market, warningCode, "N", Map.of(), Map.of());
        var input = switch (freshnessStatus) {
            case FRESH -> original;
            case EXPIRED -> withTimes(original, EVALUATED_AT.minus(MAX_MASTER_AGE),
                    EVALUATED_AT.minus(MAX_MASTER_AGE).plusSeconds(1), EVALUATED_AT.minus(MAX_BASIC_INFO_AGE),
                    EVALUATED_AT.minus(MAX_BASIC_INFO_AGE).plusSeconds(1));
            case TIME_UNVERIFIED -> withTimes(original, EVALUATED_AT.plusNanos(1), EVALUATED_AT.plusSeconds(1),
                    EVALUATED_AT.minusSeconds(100), EVALUATED_AT.minusSeconds(99));
        };
        return new KisStockRestrictionFreshnessPolicy().evaluate(request(), input);
    }
}
