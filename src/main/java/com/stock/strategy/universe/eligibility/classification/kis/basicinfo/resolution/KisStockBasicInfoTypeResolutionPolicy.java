package com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution;

import com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchReasonCode;
import com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchResult;
import com.stock.strategy.universe.eligibility.classification.kis.KisStockMasterTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.KisStockBasicInfoTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.result.KisStockBasicInfoTypeResolutionReasonCode;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.result.KisStockBasicInfoTypeResolutionResult;

import java.util.Objects;

public class KisStockBasicInfoTypeResolutionPolicy {
    public static final String RESOLUTION_VERSION = "KIS_STOCK_BASIC_INFO_CURRENT_TYPE_RESOLUTION_V1";
    private final KisStockMasterTypeClassificationPolicy masterPolicy;
    private final KisStockBasicInfoTypeClassificationPolicy basicInfoPolicy;

    public KisStockBasicInfoTypeResolutionPolicy(
            KisStockMasterTypeClassificationPolicy masterPolicy, KisStockBasicInfoTypeClassificationPolicy basicInfoPolicy
    ) {
        this.masterPolicy = Objects.requireNonNull(masterPolicy, "masterPolicy must not be null.");
        this.basicInfoPolicy = Objects.requireNonNull(basicInfoPolicy, "basicInfoPolicy must not be null.");
    }

    public KisStockBasicInfoTypeResolutionResult resolve(KisStockBasicInfoMatchResult matchingResult) {
        Objects.requireNonNull(matchingResult, "matchingResult must not be null.");
        var master = matchingResult.comparedRecord() == null ? null : Objects.requireNonNull(
                masterPolicy.classify(matchingResult.comparedMarket(), matchingResult.comparedRecord()),
                "Master classification must not be null.");
        // A failed comparison never supplies an API type or even a master-only reference type.
        if (matchingResult.reasonCode() != KisStockBasicInfoMatchReasonCode.STANDARD_CODE_AND_MARKET_MATCH) {
            return new KisStockBasicInfoTypeResolutionResult(RESOLUTION_VERSION, matchingResult, master, null, null,
                    KisStockBasicInfoTypeResolutionReasonCode.MATCH_NOT_CONFIRMED);
        }
        var api = Objects.requireNonNull(basicInfoPolicy.classify(matchingResult.apiInput()),
                "Basic info classification must not be null.");
        var masterType = master.securityType();
        var apiType = api.securityType();
        KisStockBasicInfoTypeResolutionReasonCode reason;
        if (masterType == null) {
            reason = apiType == null ? KisStockBasicInfoTypeResolutionReasonCode.TYPE_UNVERIFIED
                    : KisStockBasicInfoTypeResolutionReasonCode.BASIC_INFO_TYPE_SUPPLEMENTED;
        } else if (apiType == null) {
            reason = KisStockBasicInfoTypeResolutionReasonCode.MASTER_TYPE_ONLY;
        } else {
            reason = masterType == apiType ? KisStockBasicInfoTypeResolutionReasonCode.SOURCES_AGREE
                    : KisStockBasicInfoTypeResolutionReasonCode.TYPE_CONFLICT;
        }
        var referenceType = switch (reason) {
            case BASIC_INFO_TYPE_SUPPLEMENTED -> apiType;
            case MASTER_TYPE_ONLY, SOURCES_AGREE -> masterType;
            case MATCH_NOT_CONFIRMED, TYPE_CONFLICT, TYPE_UNVERIFIED -> null;
        };
        return new KisStockBasicInfoTypeResolutionResult(RESOLUTION_VERSION, matchingResult, master, api, referenceType, reason);
    }
}
