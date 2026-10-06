package com.stock.market.stock.master.matching.kisbasicinfo;

import com.stock.market.stock.basicinfo.provider.kis.parsing.result.KisStockBasicInfoParseResult;
import com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchReasonCode;
import com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchResult;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.record.KisStockMasterRawRecord;

import java.util.Objects;

public final class KisStockBasicInfoMatchingPolicy {
    public static final String MATCHING_VERSION = "KIS_STOCK_BASIC_INFO_STANDARD_CODE_MARKET_V1";

    public KisStockBasicInfoMatchResult match(
            StockMasterBatchParseResult masterBatch, String requestedSymbol, KisStockBasicInfoParseResult apiInput
    ) {
        Objects.requireNonNull(masterBatch, "masterBatch must not be null.");
        Objects.requireNonNull(apiInput, "apiInput must not be null.");
        if (requestedSymbol == null || requestedSymbol.isBlank()) {
            throw new IllegalArgumentException("requestedSymbol must not be blank.");
        }

        // Select only by the supplied request, never by an identifier returned by the API.
        for (var marketResult : masterBatch.marketResults()) {
            for (var record : marketResult.records()) {
                if (requestedSymbol.equals(record.symbol())) {
                    return new KisStockBasicInfoMatchResult(MATCHING_VERSION, masterBatch, requestedSymbol, apiInput,
                            marketResult.market(), record, compare(marketResult.market(), record, apiInput));
                }
            }
        }
        return new KisStockBasicInfoMatchResult(MATCHING_VERSION, masterBatch, requestedSymbol, apiInput,
                null, null, KisStockBasicInfoMatchReasonCode.REQUESTED_SYMBOL_NOT_FOUND);
    }

    private KisStockBasicInfoMatchReasonCode compare(
            KisStockMasterMarket market, KisStockMasterRawRecord record, KisStockBasicInfoParseResult apiInput
    ) {
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
