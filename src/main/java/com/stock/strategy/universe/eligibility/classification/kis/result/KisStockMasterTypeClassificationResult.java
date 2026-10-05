package com.stock.strategy.universe.eligibility.classification.kis.result;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.record.KisStockMasterRawRecord;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;

import java.util.Objects;

public record KisStockMasterTypeClassificationResult(
        KisStockMasterMarket market,
        KisStockMasterRawRecord rawRecord,
        StockSecurityType securityType,
        KisStockMasterTypeClassificationReasonCode reasonCode,
        String classificationVersion,
        String sourceRevision
) {
    public KisStockMasterTypeClassificationResult {
        Objects.requireNonNull(market, "market must not be null.");
        Objects.requireNonNull(rawRecord, "rawRecord must not be null.");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null.");
        if (classificationVersion == null || classificationVersion.isBlank()) {
            throw new IllegalArgumentException("classificationVersion must not be blank.");
        }
        if (sourceRevision == null || !sourceRevision.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("sourceRevision must be a full lowercase Git commit hash.");
        }
        if ((reasonCode == KisStockMasterTypeClassificationReasonCode.TYPE_INTERPRETED) != (securityType != null)) {
            throw new IllegalArgumentException("securityType must be present only for TYPE_INTERPRETED.");
        }
    }
}
