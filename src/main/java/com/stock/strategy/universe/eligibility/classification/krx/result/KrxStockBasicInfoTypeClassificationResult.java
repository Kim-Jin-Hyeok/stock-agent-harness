package com.stock.strategy.universe.eligibility.classification.krx.result;

import com.stock.market.stock.master.provider.krx.parsing.record.KrxStockBasicInfoRawRecord;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;

import java.util.Objects;

public record KrxStockBasicInfoTypeClassificationResult(
        KrxStockBasicInfoRawRecord rawRecord,
        StockSecurityType securityType,
        KrxStockBasicInfoTypeClassificationReasonCode reasonCode,
        String classificationVersion,
        String sourceReference,
        String sourceSha256
) {
    public KrxStockBasicInfoTypeClassificationResult {
        Objects.requireNonNull(rawRecord, "rawRecord must not be null.");
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
        if ((reasonCode == KrxStockBasicInfoTypeClassificationReasonCode.TYPE_INTERPRETED) != (securityType != null)) {
            throw new IllegalArgumentException("securityType must be present only for TYPE_INTERPRETED.");
        }
    }
}
