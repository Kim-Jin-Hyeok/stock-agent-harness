package com.stock.market.stock.master.parsing.result;

import com.stock.market.stock.master.parsing.StockMasterBatchParsingService;
import com.stock.market.stock.master.provider.kis.parsing.KisStockMasterParser;
import com.stock.market.stock.master.provider.kis.parsing.result.KisStockMasterParseResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.stock.market.stock.master.parsing.support.StockMasterBatchParsingFixture.collect;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockMasterBatchParseResultTest {
    @TempDir
    Path root;

    @Test
    void defensivelyCopiesMarketResults() throws Exception {
        var original = result();
        var results = new ArrayList<>(original.marketResults());
        var copy = new StockMasterBatchParseResult(original.collection(), results);
        results.clear();

        assertThat(copy.marketResults()).hasSize(2);
        assertThatThrownBy(() -> copy.marketResults().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsPartialDuplicateAndMissingInputs() throws Exception {
        var original = result();
        var first = original.marketResults().getFirst();
        assertThatThrownBy(() -> new StockMasterBatchParseResult(original.collection(), List.of(first)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockMasterBatchParseResult(original.collection(), List.of(first, first)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockMasterBatchParseResult(null, original.marketResults())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new StockMasterBatchParseResult(original.collection(), null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsParsedHashFromAnotherFile() throws Exception {
        var original = result();
        var first = original.marketResults().getFirst();
        var other = new KisStockMasterParseResult(first.market(), "a".repeat(64), first.parserVersion(), first.layoutVersion(), first.records());
        assertThatThrownBy(() -> new StockMasterBatchParseResult(original.collection(), List.of(other, original.marketResults().getLast())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("collection hash");
    }

    @Test
    void rejectsMixedParserAndLayoutVersions() throws Exception {
        var original = result();
        var second = original.marketResults().getLast();
        var otherParser = new KisStockMasterParseResult(second.market(), second.inputSha256(), "OTHER", second.layoutVersion(), second.records());
        var otherLayout = new KisStockMasterParseResult(second.market(), second.inputSha256(), second.parserVersion(), "OTHER", second.records());
        for (var changed : List.of(otherParser, otherLayout)) {
            assertThatThrownBy(() -> new StockMasterBatchParseResult(original.collection(), List.of(original.marketResults().getFirst(), changed)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("consistent parser and layout versions");
        }
    }

    private StockMasterBatchParseResult result() throws Exception {
        var collection = collect(root);
        return new StockMasterBatchParsingService(new KisStockMasterParser()).parseBatch(root, collection.collectionId());
    }
}
