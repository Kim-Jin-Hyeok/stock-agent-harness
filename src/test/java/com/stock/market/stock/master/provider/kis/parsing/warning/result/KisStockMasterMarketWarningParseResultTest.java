package com.stock.market.stock.master.provider.kis.parsing.warning.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.warning.KisStockMasterMarketWarningParser;
import com.stock.market.stock.master.provider.kis.parsing.warning.record.KisStockMasterMarketWarningRawRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.market.stock.master.provider.kis.parsing.warning.support.KisStockMasterMarketWarningFixture.multiRowSource;
import static com.stock.market.stock.master.provider.kis.parsing.warning.support.KisStockMasterMarketWarningFixture.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockMasterMarketWarningParseResultTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final KisStockMasterMarketWarningParser parser = new KisStockMasterMarketWarningParser();

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void preservesEntireSourceAndRecordsAcrossJsonRoundTrip(KisStockMasterMarket market) throws Exception {
        var result = parser.parse(multiRowSource(market));
        assertThat(mapper.readValue(mapper.writeValueAsBytes(result), KisStockMasterMarketWarningParseResult.class)).isEqualTo(result);
    }

    @Test
    void copiesRecordsAndRejectsNullMembersAndMissingDependencies() {
        var source = source(KOSPI, "00", "N");
        var original = parser.parse(source);
        var mutable = new ArrayList<>(original.records());
        var result = new KisStockMasterMarketWarningParseResult(source, mutable, original.parserVersion(), original.sourceRevision());
        mutable.clear();
        assertThat(result.records()).isEqualTo(original.records());
        assertThatThrownBy(() -> result.records().clear()).isInstanceOf(UnsupportedOperationException.class);
        mutable.add(null);
        assertThatThrownBy(() -> new KisStockMasterMarketWarningParseResult(source, mutable, result.parserVersion(), result.sourceRevision()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockMasterMarketWarningParseResult(null, result.records(), result.parserVersion(), result.sourceRevision()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockMasterMarketWarningParseResult(source, null, result.parserVersion(), result.sourceRevision()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsMissingDuplicatedReorderedOrDifferentRows() {
        var result = parser.parse(multiRowSource(KOSPI));
        var removed = new ArrayList<>(result.records());
        removed.removeFirst();
        var duplicate = new ArrayList<>(result.records());
        duplicate.add(duplicate.getFirst());
        var reordered = new ArrayList<>(result.records());
        Collections.reverse(reordered);
        var replaced = new ArrayList<>(result.records());
        replaced.set(0, new KisStockMasterMarketWarningRawRecord(1, "999999", "KR7005930003", "00", "N"));
        for (var records : List.of(List.<KisStockMasterMarketWarningRawRecord>of(), removed, duplicate, reordered, replaced)) {
            assertThatThrownBy(() -> new KisStockMasterMarketWarningParseResult(result.source(), records, result.parserVersion(), result.sourceRevision()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"code", "preannouncement", "symbol", "standardCode", "lineNumber", "source", "sourceHash", "sourceVersion", "parserVersion", "sourceRevision"})
    void rejectsJsonWithAlteredValuesSourceOrProvenance(String change) {
        var result = parser.parse(source(KOSPI, "02", "Y"));
        ObjectNode node = mapper.valueToTree(result);
        ObjectNode record = (ObjectNode) node.withArray("records").get(0);
        ObjectNode source = (ObjectNode) node.get("source");
        switch (change) {
            case "code" -> record.put("rawMarketWarningCode", "00");
            case "preannouncement" -> record.put("rawMarketWarningRiskPreannouncement", "N");
            case "symbol" -> record.put("symbol", "999999");
            case "standardCode" -> record.put("standardCode", "KR7999999999");
            case "lineNumber" -> record.put("lineNumber", 2);
            case "source" -> node.set("source", mapper.valueToTree(source(KOSPI, "00", "N")));
            case "sourceHash" -> source.put("inputSha256", "0".repeat(64));
            case "sourceVersion" -> source.put("layoutVersion", "UNKNOWN");
            case "parserVersion" -> node.put("parserVersion", "UNKNOWN");
            case "sourceRevision" -> node.put("sourceRevision", "0".repeat(40));
            default -> throw new IllegalArgumentException("Unexpected test change.");
        }
        assertThatThrownBy(() -> mapper.treeToValue(node, KisStockMasterMarketWarningParseResult.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"parserVersion", "sourceRevision"})
    void rejectsBlankOrMissingResultProvenance(String field) {
        var result = parser.parse(source(KOSPI, "00", "N"));
        for (String value : new String[]{null, "", " "}) {
            assertThatThrownBy(() -> new KisStockMasterMarketWarningParseResult(result.source(), result.records(),
                    field.equals("parserVersion") ? value : result.parserVersion(),
                    field.equals("sourceRevision") ? value : result.sourceRevision())).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
