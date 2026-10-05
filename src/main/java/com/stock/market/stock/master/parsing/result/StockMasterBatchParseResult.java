package com.stock.market.stock.master.parsing.result;

import com.stock.market.stock.master.collection.result.StockMasterCollectionResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.result.KisStockMasterParseResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record StockMasterBatchParseResult(
        StockMasterCollectionResult collection,
        List<KisStockMasterParseResult> marketResults
) {
    public StockMasterBatchParseResult {
        Objects.requireNonNull(collection, "collection must not be null.");
        marketResults = List.copyOf(marketResults);
        if (marketResults.size() != KisStockMasterMarket.values().length
                || marketResults.stream().map(KisStockMasterParseResult::market).distinct().count() != marketResults.size()) {
            throw new IllegalArgumentException("Batch parsing must contain both markets exactly once.");
        }
        var first = marketResults.getFirst();
        var symbols = new HashMap<String, IdentifierOrigin>();
        var standardCodes = new HashMap<String, IdentifierOrigin>();
        for (var result : marketResults) {
            var file = collection.files().stream().filter(observation -> observation.market() == result.market())
                    .findFirst().orElseThrow();
            if (!file.extractedSha256().equals(result.inputSha256())) {
                throw new IllegalArgumentException("Parsed input does not match the collection hash. market=" + result.market());
            }
            if (!first.parserVersion().equals(result.parserVersion()) || !first.layoutVersion().equals(result.layoutVersion())) {
                throw new IllegalArgumentException("Batch parsing must use consistent parser and layout versions.");
            }
            for (var record : result.records()) {
                var origin = new IdentifierOrigin(result.market(), record.lineNumber());
                requireUnique(symbols, record.symbol(), "symbol", origin);
                requireUnique(standardCodes, record.standardCode(), "standardCode", origin);
            }
        }
    }

    private static void requireUnique(Map<String, IdentifierOrigin> origins, String value, String field, IdentifierOrigin origin) {
        var first = origins.putIfAbsent(value, origin);
        if (first != null) {
            throw new IllegalArgumentException("Batch " + field + " collision. market=" + origin.market()
                    + ", line=" + origin.line() + ", firstMarket=" + first.market() + ", firstLine=" + first.line());
        }
    }

    private record IdentifierOrigin(KisStockMasterMarket market, int line) {
    }
}
