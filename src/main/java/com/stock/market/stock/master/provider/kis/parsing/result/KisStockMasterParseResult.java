package com.stock.market.stock.master.provider.kis.parsing.result;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.record.KisStockMasterRawRecord;

import java.util.List;
import java.util.Objects;

public record KisStockMasterParseResult(
        KisStockMasterMarket market,
        String inputSha256,
        String parserVersion,
        String layoutVersion,
        List<KisStockMasterRawRecord> records
) {
    public KisStockMasterParseResult {
        Objects.requireNonNull(market, "market must not be null.");
        if (inputSha256 == null || !inputSha256.matches("[0-9a-f]{64}")
                || parserVersion == null || parserVersion.isBlank()
                || layoutVersion == null || layoutVersion.isBlank()) {
            throw new IllegalArgumentException("Input SHA-256 and parser/layout versions must be present.");
        }
        records = List.copyOf(records);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("Stock master parse result must contain records.");
        }
        for (int index = 0; index < records.size(); index++) {
            if (records.get(index).lineNumber() != index + 1) {
                throw new IllegalArgumentException("Stock master records must retain consecutive source line numbers.");
            }
        }
    }
}
