package com.stock.market.stock.master.matching.kiskrx;

import com.stock.market.stock.master.matching.kiskrx.result.KisKrxStockIdentityMatchReasonCode;
import com.stock.market.stock.master.matching.kiskrx.result.KisKrxStockIdentityMatchResult;
import com.stock.market.stock.master.matching.kiskrx.result.KisKrxStockIdentityMatchingResult;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.record.KisStockMasterRawRecord;
import com.stock.market.stock.master.provider.krx.parsing.record.KrxStockBasicInfoRawRecord;
import com.stock.market.stock.master.provider.krx.parsing.result.KrxStockBasicInfoParseResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class KisKrxStockIdentityMatchingPolicy {
    public static final String MATCHING_VERSION = "KIS_KRX_STOCK_IDENTITY_MATCH_V1";

    public KisKrxStockIdentityMatchingResult match(
            StockMasterBatchParseResult kisBatch,
            Map<KisStockMasterMarket, KrxStockBasicInfoParseResult> krxInputs
    ) {
        Objects.requireNonNull(kisBatch, "kisBatch must not be null.");
        var inputs = Map.copyOf(krxInputs);
        if (!inputs.keySet().equals(Set.of(KisStockMasterMarket.values()))) {
            throw new IllegalArgumentException("KRX inputs must contain both request markets exactly once.");
        }
        var byStandardCode = new HashMap<String, KisOrigin>();
        var bySymbol = new HashMap<String, KisOrigin>();
        for (var input : kisBatch.marketResults()) {
            for (var record : input.records()) {
                var origin = new KisOrigin(input.market(), record);
                byStandardCode.put(record.standardCode(), origin);
                bySymbol.put(record.symbol(), origin);
            }
        }
        // Count across both responses before matching so all members of a collision are deferred.
        var standardCounts = new HashMap<String, Integer>();
        var symbolCounts = new HashMap<String, Integer>();
        for (var market : KisStockMasterMarket.values()) {
            for (var row : inputs.get(market).records()) {
                if (!row.standardCode().isBlank()) {
                    standardCounts.merge(row.standardCode(), 1, Integer::sum);
                }
                if (!row.symbol().isBlank()) {
                    symbolCounts.merge(row.symbol(), 1, Integer::sum);
                }
            }
        }
        var results = new ArrayList<KisKrxStockIdentityMatchResult>();
        var matchedCodes = new HashSet<String>();
        for (var market : KisStockMasterMarket.values()) {
            for (var row : inputs.get(market).records()) {
                var reason = reason(market, row, standardCounts, symbolCounts, byStandardCode, bySymbol);
                var matched = reason == KisKrxStockIdentityMatchReasonCode.EXACT_IDENTITY_MATCH
                        ? byStandardCode.get(row.standardCode()).record() : null;
                results.add(new KisKrxStockIdentityMatchResult(market, row, matched, reason));
                if (matched != null) {
                    matchedCodes.add(matched.standardCode());
                }
            }
        }
        var unmatched = new EnumMap<KisStockMasterMarket, List<KisStockMasterRawRecord>>(KisStockMasterMarket.class);
        for (var input : kisBatch.marketResults()) {
            unmatched.put(input.market(), input.records().stream()
                    .filter(row -> !matchedCodes.contains(row.standardCode())).toList());
        }
        return new KisKrxStockIdentityMatchingResult(MATCHING_VERSION, kisBatch, inputs, results, unmatched);
    }

    private static KisKrxStockIdentityMatchReasonCode reason(
            KisStockMasterMarket market, KrxStockBasicInfoRawRecord row,
            Map<String, Integer> standardCounts, Map<String, Integer> symbolCounts,
            Map<String, KisOrigin> byStandardCode, Map<String, KisOrigin> bySymbol
    ) {
        if (row.standardCode().isBlank() || row.symbol().isBlank()) {
            return KisKrxStockIdentityMatchReasonCode.KRX_IDENTIFIER_BLANK;
        }
        if (standardCounts.get(row.standardCode()) > 1 || symbolCounts.get(row.symbol()) > 1) {
            return KisKrxStockIdentityMatchReasonCode.KRX_IDENTIFIER_DUPLICATED;
        }
        if (!market.name().equals(row.rawMarket())) {
            return KisKrxStockIdentityMatchReasonCode.REQUEST_MARKET_MISMATCH;
        }
        var kis = byStandardCode.get(row.standardCode());
        if (kis == null) {
            return bySymbol.containsKey(row.symbol()) ? KisKrxStockIdentityMatchReasonCode.SYMBOL_ONLY_MATCH
                    : KisKrxStockIdentityMatchReasonCode.STANDARD_CODE_NOT_FOUND;
        }
        if (!kis.record().symbol().equals(row.symbol())) {
            return KisKrxStockIdentityMatchReasonCode.SYMBOL_MISMATCH;
        }
        if (kis.market() != market) {
            return KisKrxStockIdentityMatchReasonCode.MARKET_MISMATCH;
        }
        return KisKrxStockIdentityMatchReasonCode.EXACT_IDENTITY_MATCH;
    }

    private record KisOrigin(KisStockMasterMarket market, KisStockMasterRawRecord record) {
    }
}
