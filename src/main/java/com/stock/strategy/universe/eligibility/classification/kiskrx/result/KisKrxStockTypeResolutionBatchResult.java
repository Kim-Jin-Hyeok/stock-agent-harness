package com.stock.strategy.universe.eligibility.classification.kiskrx.result;

import com.stock.market.stock.master.matching.kiskrx.result.KisKrxStockIdentityMatchResult;
import com.stock.market.stock.master.matching.kiskrx.result.KisKrxStockIdentityMatchingResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.result.KisStockMasterParseResult;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public record KisKrxStockTypeResolutionBatchResult(
        String resolutionVersion,
        KisKrxStockIdentityMatchingResult matchingResult,
        List<KisKrxStockTypeResolutionResult> rowResults
) {
    public KisKrxStockTypeResolutionBatchResult {
        if (resolutionVersion == null || resolutionVersion.isBlank()) {
            throw new IllegalArgumentException("resolutionVersion must not be blank.");
        }
        Objects.requireNonNull(matchingResult, "matchingResult must not be null.");
        rowResults = List.copyOf(rowResults);
        var matches = new HashMap<String, KisKrxStockIdentityMatchResult>();
        for (var row : matchingResult.rowResults()) {
            if (row.matchedKisRecord() != null) {
                matches.put(row.matchedKisRecord().standardCode(), row);
            }
        }
        var inputs = new EnumMap<KisStockMasterMarket, KisStockMasterParseResult>(KisStockMasterMarket.class);
        matchingResult.kisBatch().marketResults().forEach(input -> inputs.put(input.market(), input));
        int index = 0;
        for (var market : KisStockMasterMarket.values()) {
            for (var raw : inputs.get(market).records()) {
                if (index >= rowResults.size()) {
                    throw new IllegalArgumentException("Every KIS input row must have one resolution result in source order.");
                }
                var row = rowResults.get(index++);
                if (row.kisClassification().market() != market || !row.kisClassification().rawRecord().equals(raw)
                        || !Objects.equals(row.identityMatch(), matches.get(raw.standardCode()))) {
                    throw new IllegalArgumentException("Resolution results must preserve KIS inputs and their exact identity matches.");
                }
            }
        }
        if (index != rowResults.size()) {
            throw new IllegalArgumentException("Resolution results must not add KIS rows.");
        }
    }
}
