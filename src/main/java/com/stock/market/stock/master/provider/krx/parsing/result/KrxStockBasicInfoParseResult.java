package com.stock.market.stock.master.provider.krx.parsing.result;

import com.stock.market.stock.master.provider.krx.parsing.record.KrxStockBasicInfoRawRecord;

import java.util.List;

public record KrxStockBasicInfoParseResult(
        String inputSha256,
        String parserVersion,
        List<KrxStockBasicInfoRawRecord> records
) {
    public KrxStockBasicInfoParseResult {
        if (inputSha256 == null || !inputSha256.matches("[0-9a-f]{64}")
                || parserVersion == null || parserVersion.isBlank()) {
            throw new IllegalArgumentException("Input SHA-256 and parser version must be present.");
        }
        records = List.copyOf(records);
        // An empty response does not prove a complete population or an unlisted stock.
        for (int index = 0; index < records.size(); index++) {
            if (records.get(index).rowNumber() != index + 1) {
                throw new IllegalArgumentException("KRX records must retain consecutive source row numbers.");
            }
        }
    }
}
