package com.stock.strategy.universe.eligibility.classification.kiskrx;

import com.stock.market.stock.master.matching.kiskrx.result.KisKrxStockIdentityMatchResult;
import com.stock.market.stock.master.matching.kiskrx.result.KisKrxStockIdentityMatchingResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.result.KisStockMasterParseResult;
import com.stock.strategy.universe.eligibility.classification.kis.KisStockMasterTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.classification.krx.KrxStockBasicInfoTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.classification.kiskrx.result.KisKrxStockTypeResolutionBatchResult;
import com.stock.strategy.universe.eligibility.classification.kiskrx.result.KisKrxStockTypeResolutionReasonCode;
import com.stock.strategy.universe.eligibility.classification.kiskrx.result.KisKrxStockTypeResolutionResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Objects;

public class KisKrxStockTypeResolutionPolicy {
    public static final String RESOLUTION_VERSION = "KIS_KRX_CURRENT_TYPE_RESOLUTION_V1";
    private final KisStockMasterTypeClassificationPolicy kisPolicy;
    private final KrxStockBasicInfoTypeClassificationPolicy krxPolicy;

    public KisKrxStockTypeResolutionPolicy(
            KisStockMasterTypeClassificationPolicy kisPolicy,
            KrxStockBasicInfoTypeClassificationPolicy krxPolicy
    ) {
        this.kisPolicy = Objects.requireNonNull(kisPolicy, "kisPolicy must not be null.");
        this.krxPolicy = Objects.requireNonNull(krxPolicy, "krxPolicy must not be null.");
    }

    public KisKrxStockTypeResolutionBatchResult resolve(KisKrxStockIdentityMatchingResult matchingResult) {
        Objects.requireNonNull(matchingResult, "matchingResult must not be null.");
        var matches = new HashMap<String, KisKrxStockIdentityMatchResult>();
        for (var row : matchingResult.rowResults()) {
            if (row.matchedKisRecord() != null) {
                matches.put(row.matchedKisRecord().standardCode(), row);
            }
        }
        var inputs = new EnumMap<KisStockMasterMarket, KisStockMasterParseResult>(KisStockMasterMarket.class);
        matchingResult.kisBatch().marketResults().forEach(input -> inputs.put(input.market(), input));
        var results = new ArrayList<KisKrxStockTypeResolutionResult>();
        for (var market : KisStockMasterMarket.values()) {
            for (var raw : inputs.get(market).records()) {
                var kis = Objects.requireNonNull(kisPolicy.classify(market, raw), "KIS classification must not be null.");
                var match = matches.get(raw.standardCode());
                // Only exact matches may supply KRX type evidence; all other diagnostics stay in the batch input.
                var krx = match == null ? null : Objects.requireNonNull(
                        krxPolicy.classify(match.krxRecord()), "KRX classification must not be null."
                );
                var kisType = kis.securityType();
                var krxType = krx == null ? null : krx.securityType();
                KisKrxStockTypeResolutionReasonCode reason;
                if (kisType == null) {
                    reason = krxType == null ? KisKrxStockTypeResolutionReasonCode.TYPE_UNVERIFIED
                            : KisKrxStockTypeResolutionReasonCode.KRX_TYPE_SUPPLEMENTED;
                } else if (krxType == null) {
                    reason = KisKrxStockTypeResolutionReasonCode.KIS_TYPE_ONLY;
                } else {
                    reason = kisType == krxType ? KisKrxStockTypeResolutionReasonCode.SOURCES_AGREE
                            : KisKrxStockTypeResolutionReasonCode.TYPE_CONFLICT;
                }
                var referenceType = switch (reason) {
                    case KIS_TYPE_ONLY, SOURCES_AGREE -> kisType;
                    case KRX_TYPE_SUPPLEMENTED -> krxType;
                    case TYPE_CONFLICT, TYPE_UNVERIFIED -> null;
                };
                results.add(new KisKrxStockTypeResolutionResult(kis, match, krx, referenceType, reason));
            }
        }
        return new KisKrxStockTypeResolutionBatchResult(RESOLUTION_VERSION, matchingResult, results);
    }
}
