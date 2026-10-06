package com.stock.market.stock.master.matching.kisbasicinfo.result;

import com.stock.market.stock.basicinfo.provider.kis.parsing.result.KisStockBasicInfoParseResult;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.record.KisStockMasterRawRecord;

import java.util.Objects;

public record KisStockBasicInfoMatchResult(
        String matchingVersion,
        StockMasterBatchParseResult masterBatch,
        String requestedSymbol,
        KisStockBasicInfoParseResult apiInput,
        KisStockMasterMarket comparedMarket,
        KisStockMasterRawRecord comparedRecord,
        KisStockBasicInfoMatchReasonCode reasonCode
) {
    public KisStockBasicInfoMatchResult {
        if (matchingVersion == null || matchingVersion.isBlank()) {
            throw new IllegalArgumentException("matchingVersion must not be blank.");
        }
        if (requestedSymbol == null || requestedSymbol.isBlank()) {
            throw new IllegalArgumentException("requestedSymbol must not be blank.");
        }
        Objects.requireNonNull(masterBatch, "masterBatch must not be null.");
        Objects.requireNonNull(apiInput, "apiInput must not be null.");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null.");

        KisStockMasterMarket expectedMarket = null;
        KisStockMasterRawRecord expectedRecord = null;
        for (var marketResult : masterBatch.marketResults()) {
            for (var record : marketResult.records()) {
                if (requestedSymbol.equals(record.symbol())) {
                    expectedMarket = marketResult.market();
                    expectedRecord = record;
                }
            }
        }
        if (!Objects.equals(comparedMarket, expectedMarket) || !Objects.equals(comparedRecord, expectedRecord)) {
            throw new IllegalArgumentException("Compared market and record must belong to the requested symbol in the master batch.");
        }
        if (reasonCode != expectedReason(expectedMarket, expectedRecord, apiInput)) {
            throw new IllegalArgumentException("Match reason must agree with the requested master record and API input.");
        }
    }

    private static KisStockBasicInfoMatchReasonCode expectedReason(
            KisStockMasterMarket market, KisStockMasterRawRecord record, KisStockBasicInfoParseResult apiInput
    ) {
        if (record == null) {
            return KisStockBasicInfoMatchReasonCode.REQUESTED_SYMBOL_NOT_FOUND;
        }
        var raw = apiInput.rawRecord();
        if (raw.standardCode().isBlank()) {
            return KisStockBasicInfoMatchReasonCode.API_STANDARD_CODE_BLANK;
        }
        if (!record.standardCode().equals(raw.standardCode())) {
            return KisStockBasicInfoMatchReasonCode.STANDARD_CODE_MISMATCH;
        }
        var apiMarket = switch (raw.rawMarket()) {
            case "STK" -> KisStockMasterMarket.KOSPI;
            case "KSQ" -> KisStockMasterMarket.KOSDAQ;
            default -> null;
        };
        if (apiMarket == null) {
            return KisStockBasicInfoMatchReasonCode.API_MARKET_UNVERIFIED;
        }
        return market == apiMarket ? KisStockBasicInfoMatchReasonCode.STANDARD_CODE_AND_MARKET_MATCH
                : KisStockBasicInfoMatchReasonCode.MARKET_MISMATCH;
    }
}
