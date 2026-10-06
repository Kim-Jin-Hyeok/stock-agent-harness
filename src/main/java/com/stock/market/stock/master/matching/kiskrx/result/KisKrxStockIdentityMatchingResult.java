package com.stock.market.stock.master.matching.kiskrx.result;

import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.record.KisStockMasterRawRecord;
import com.stock.market.stock.master.provider.krx.parsing.result.KrxStockBasicInfoParseResult;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record KisKrxStockIdentityMatchingResult(
        String matchingVersion,
        StockMasterBatchParseResult kisBatch,
        Map<KisStockMasterMarket, KrxStockBasicInfoParseResult> krxInputs,
        List<KisKrxStockIdentityMatchResult> rowResults,
        Map<KisStockMasterMarket, List<KisStockMasterRawRecord>> unmatchedKisRecords
) {
    public KisKrxStockIdentityMatchingResult {
        if (matchingVersion == null || matchingVersion.isBlank()) {
            throw new IllegalArgumentException("matchingVersion must not be blank.");
        }
        Objects.requireNonNull(kisBatch, "kisBatch must not be null.");
        krxInputs = Map.copyOf(krxInputs);
        rowResults = List.copyOf(rowResults);
        var remaining = new EnumMap<KisStockMasterMarket, List<KisStockMasterRawRecord>>(KisStockMasterMarket.class);
        unmatchedKisRecords.forEach((market, rows) -> remaining.put(market, List.copyOf(rows)));
        unmatchedKisRecords = Map.copyOf(remaining);
        var markets = Set.of(KisStockMasterMarket.values());
        if (!krxInputs.keySet().equals(markets) || !unmatchedKisRecords.keySet().equals(markets)) {
            throw new IllegalArgumentException("KRX inputs and unmatched KIS rows must contain both markets.");
        }

        var kisByMarket = new EnumMap<KisStockMasterMarket, Map<String, KisStockMasterRawRecord>>(KisStockMasterMarket.class);
        for (var input : kisBatch.marketResults()) {
            var records = new HashMap<String, KisStockMasterRawRecord>();
            input.records().forEach(row -> records.put(row.standardCode(), row));
            kisByMarket.put(input.market(), records);
        }
        var matchedCodes = new HashSet<String>();
        int index = 0;
        for (var market : KisStockMasterMarket.values()) {
            for (var raw : krxInputs.get(market).records()) {
                if (index >= rowResults.size()) {
                    throw new IllegalArgumentException("Every KRX input row must have one result in source order.");
                }
                var result = rowResults.get(index++);
                if (result.requestMarket() != market || !result.krxRecord().equals(raw)) {
                    throw new IllegalArgumentException("Every KRX result must preserve its input row and market.");
                }
                var matched = result.matchedKisRecord();
                if (matched != null && (!matched.equals(kisByMarket.get(market).get(matched.standardCode()))
                        || !matchedCodes.add(matched.standardCode()))) {
                    throw new IllegalArgumentException("Matched KIS rows must belong to the market input and be unique.");
                }
            }
        }
        if (index != rowResults.size()) {
            throw new IllegalArgumentException("Results must not add KRX rows.");
        }
        for (var input : kisBatch.marketResults()) {
            var expected = input.records().stream().filter(row -> !matchedCodes.contains(row.standardCode())).toList();
            var actual = unmatchedKisRecords.get(input.market());
            if (!actual.equals(expected)) {
                throw new IllegalArgumentException("Every unmatched KIS input row must be preserved.");
            }
        }
    }
}
