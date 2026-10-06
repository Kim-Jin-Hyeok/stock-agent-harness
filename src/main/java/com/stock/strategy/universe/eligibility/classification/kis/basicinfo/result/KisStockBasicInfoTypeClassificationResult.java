package com.stock.strategy.universe.eligibility.classification.kis.basicinfo.result;

import com.stock.market.stock.basicinfo.provider.kis.parsing.result.KisStockBasicInfoParseResult;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;

import java.util.Objects;

public record KisStockBasicInfoTypeClassificationResult(
        KisStockBasicInfoParseResult parseResult,
        StockSecurityType securityType,
        KisStockBasicInfoTypeClassificationReasonCode reasonCode,
        String classificationVersion,
        String sourceReference,
        String sourceSha256
) {
    public KisStockBasicInfoTypeClassificationResult {
        Objects.requireNonNull(parseResult, "parseResult must not be null.");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null.");
        if (classificationVersion == null || classificationVersion.isBlank()) {
            throw new IllegalArgumentException("classificationVersion must not be blank.");
        }
        if (sourceReference == null || sourceReference.isBlank()) {
            throw new IllegalArgumentException("sourceReference must not be blank.");
        }
        if (sourceSha256 == null || !sourceSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sourceSha256 must be a full lowercase SHA-256.");
        }
        if ((reasonCode == KisStockBasicInfoTypeClassificationReasonCode.TYPE_INTERPRETED) != (securityType != null)) {
            throw new IllegalArgumentException("securityType must be present only for TYPE_INTERPRETED.");
        }
        if (securityType != null && !securityType.isIndividualStock()) {
            throw new IllegalArgumentException("Interpreted securityType must be COMMON_STOCK or PREFERRED_STOCK.");
        }
    }
}
