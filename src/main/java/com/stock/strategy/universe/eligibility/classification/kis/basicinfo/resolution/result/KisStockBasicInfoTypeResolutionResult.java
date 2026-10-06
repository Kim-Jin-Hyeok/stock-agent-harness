package com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.result;

import com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchReasonCode;
import com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchResult;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.result.KisStockBasicInfoTypeClassificationResult;
import com.stock.strategy.universe.eligibility.classification.kis.result.KisStockMasterTypeClassificationResult;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;

import java.util.Objects;

public record KisStockBasicInfoTypeResolutionResult(
        String resolutionVersion,
        KisStockBasicInfoMatchResult matchingResult,
        KisStockMasterTypeClassificationResult masterClassification,
        KisStockBasicInfoTypeClassificationResult basicInfoClassification,
        StockSecurityType referenceSecurityType,
        KisStockBasicInfoTypeResolutionReasonCode reasonCode
) {
    public KisStockBasicInfoTypeResolutionResult {
        if (resolutionVersion == null || resolutionVersion.isBlank()) {
            throw new IllegalArgumentException("resolutionVersion must not be blank.");
        }
        Objects.requireNonNull(matchingResult, "matchingResult must not be null.");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null.");

        if ((matchingResult.comparedRecord() != null) != (masterClassification != null)) {
            throw new IllegalArgumentException("Master classification must be present exactly when the requested master record exists.");
        }
        if (masterClassification != null && (masterClassification.market() != matchingResult.comparedMarket()
                || !masterClassification.rawRecord().equals(matchingResult.comparedRecord()))) {
            throw new IllegalArgumentException("Master classification must preserve the compared market and complete record.");
        }
        boolean confirmed = matchingResult.reasonCode() == KisStockBasicInfoMatchReasonCode.STANDARD_CODE_AND_MARKET_MATCH;
        if (confirmed != (basicInfoClassification != null)) {
            throw new IllegalArgumentException("Basic info classification requires a successful standard code and market match and vice versa.");
        }
        if (basicInfoClassification != null && !basicInfoClassification.parseResult().equals(matchingResult.apiInput())) {
            throw new IllegalArgumentException("Basic info classification must preserve the complete matched API input.");
        }

        var masterType = masterClassification == null ? null : masterClassification.securityType();
        var apiType = basicInfoClassification == null ? null : basicInfoClassification.securityType();
        boolean consistent = switch (reasonCode) {
            case MATCH_NOT_CONFIRMED -> !confirmed && referenceSecurityType == null;
            case BASIC_INFO_TYPE_SUPPLEMENTED -> confirmed && masterType == null && apiType != null
                    && referenceSecurityType == apiType;
            case MASTER_TYPE_ONLY -> confirmed && masterType != null && apiType == null
                    && referenceSecurityType == masterType;
            case SOURCES_AGREE -> confirmed && masterType != null && masterType == apiType
                    && referenceSecurityType == masterType;
            case TYPE_CONFLICT -> confirmed && masterType != null && apiType != null && masterType != apiType
                    && referenceSecurityType == null;
            case TYPE_UNVERIFIED -> confirmed && masterType == null && apiType == null && referenceSecurityType == null;
        };
        if (!consistent) {
            throw new IllegalArgumentException("Reference type and resolution reason must agree with the match and both source classifications.");
        }
    }
}
