package com.stock.market.stock.master.provider.kis.parsing.warning.result;

import com.stock.market.stock.master.provider.kis.parsing.result.KisStockMasterParseResult;
import com.stock.market.stock.master.provider.kis.parsing.warning.KisStockMasterMarketWarningParser;
import com.stock.market.stock.master.provider.kis.parsing.warning.record.KisStockMasterMarketWarningRawRecord;

import java.util.List;
import java.util.Objects;

public record KisStockMasterMarketWarningParseResult(
        KisStockMasterParseResult source,
        List<KisStockMasterMarketWarningRawRecord> records,
        String parserVersion,
        String sourceRevision
) {
    public KisStockMasterMarketWarningParseResult {
        Objects.requireNonNull(source, "source must not be null.");
        records = List.copyOf(Objects.requireNonNull(records, "records must not be null."));
        if (!KisStockMasterMarketWarningParser.PARSER_VERSION.equals(parserVersion)) {
            throw new IllegalArgumentException("parserVersion must be the supported market warning extraction version.");
        }
        if (!KisStockMasterMarketWarningParser.SOURCE_REVISION.equals(sourceRevision)) {
            throw new IllegalArgumentException("sourceRevision must be the verified market warning layout revision.");
        }
        if (!records.equals(KisStockMasterMarketWarningParser.extractRecords(source))) {
            throw new IllegalArgumentException("Market warning records must preserve every source row, identifiers, raw fields and their order.");
        }
    }
}
