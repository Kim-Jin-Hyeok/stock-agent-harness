package com.stock.market.stock.master.provider.kis.parsing.warning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.result.KisStockMasterParseResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.market.stock.master.provider.kis.parsing.warning.support.KisStockMasterMarketWarningFixture.multiRowSource;
import static com.stock.market.stock.master.provider.kis.parsing.warning.support.KisStockMasterMarketWarningFixture.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockMasterMarketWarningParserTest {
    private final KisStockMasterMarketWarningParser parser = new KisStockMasterMarketWarningParser();
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void extractsByteOffsetsWithoutChangingSourceOrAdjacentFields(KisStockMasterMarket market) {
        var source = source(market, "02", "Y");
        var result = parser.parse(source);
        var record = result.records().getFirst();
        assertThat(result.source()).isSameAs(source);
        assertThat(result.parserVersion()).isEqualTo("KIS_STOCK_MASTER_MARKET_WARNING_RAW_V1");
        assertThat(result.sourceRevision()).isEqualTo("277ec0eb7a9b7f63b6807829286c80f36649dad2");
        assertThat(record.lineNumber()).isEqualTo(1);
        assertThat(record.symbol()).isEqualTo("005930");
        assertThat(record.standardCode()).isEqualTo("KR7005930003");
        assertThat(record.rawMarketWarningCode()).isEqualTo("02");
        assertThat(record.rawMarketWarningRiskPreannouncement()).isEqualTo("Y");
        assertThat(source.records().getFirst().rawManagement()).isEqualTo("?");
        assertThat(source.records().getFirst().rawSuspension()).isEqualTo("N");
        assertThat(source.records().getFirst().rawInvestmentCaution()).isEqualTo(market == KOSPI ? null : "N");
    }

    @ParameterizedTest
    @ValueSource(strings = {"00", "01", "02", "03", "  ", "0 ", " 0", "??", "ab", "99"})
    void preservesBlankAndUndefinedCodesWithoutInterpretingThem(String code) {
        for (var market : KisStockMasterMarket.values()) {
            for (String preannouncement : new String[]{"Y", "N", " ", "y", "n", "?", "0"}) {
                var result = parser.parse(source(market, code, preannouncement));
                assertThat(result.records().getFirst().rawMarketWarningCode()).isEqualTo(code);
                assertThat(result.records().getFirst().rawMarketWarningRiskPreannouncement()).isEqualTo(preannouncement);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void retainsEveryRowItsIdentifiersAndOriginalOrder(KisStockMasterMarket market) {
        var source = multiRowSource(market);
        var result = parser.parse(source);
        assertThat(result.records()).extracting("lineNumber").containsExactly(1, 2, 3);
        assertThat(result.records()).extracting("symbol").containsExactly("005930", "0001A0", "Q520100");
        assertThat(result.records()).extracting("standardCode").containsExactly("KR7005930003", "KR70001A0001", "KRG520001006");
        assertThat(result.records()).extracting("rawMarketWarningCode").containsExactly("00", "03", " ?");
        assertThat(result.records()).extracting("rawMarketWarningRiskPreannouncement").containsExactly("N", "Y", "y");
        assertThat(result.source()).isSameAs(source);
    }

    @ParameterizedTest
    @ValueSource(strings = {"parserVersion", "layoutVersion"})
    void refusesUnverifiedSourceVersionsInsteadOfGuessingOffsets(String field) throws Exception {
        ObjectNode changed = mapper.valueToTree(source(KOSPI, "00", "N"));
        changed.put(field, "UNKNOWN");
        var source = mapper.treeToValue(changed, KisStockMasterParseResult.class);
        assertThatThrownBy(() -> parser.parse(source)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hash", "symbol", "standardCode", "field", "shortLine", "longLine", "unmappable", "surrogate", "tailControl"})
    void rejectsAReconstructedSourceThatDisagreesWithItsHashFieldsOrEncoding(String change) throws Exception {
        ObjectNode node = mapper.valueToTree(source(KOSPI, "02", "Y"));
        ObjectNode record = (ObjectNode) node.withArray("records").get(0);
        String raw = record.get("rawLine").textValue();
        switch (change) {
            case "hash" -> node.put("inputSha256", "0".repeat(64));
            case "symbol" -> record.put("symbol", "999999");
            case "standardCode" -> record.put("standardCode", "KR7999999999");
            case "field" -> record.put("rawSuspension", "Y");
            case "shortLine" -> record.put("rawLine", raw.substring(0, raw.length() - 1));
            case "longLine" -> record.put("rawLine", raw + "X");
            case "unmappable" -> record.put("rawLine", raw.substring(0, 21) + "\u2603" + raw.substring(22));
            case "surrogate" -> record.put("rawLine", raw.substring(0, 21) + "\ud800" + raw.substring(22));
            case "tailControl" -> record.put("rawLine", raw.substring(0, 130) + "\t" + raw.substring(131));
            default -> throw new IllegalArgumentException("Unexpected test change.");
        }
        var source = mapper.treeToValue(node, KisStockMasterParseResult.class);
        assertThatThrownBy(() -> parser.parse(source)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullAndDoesNotRetainPreviousRequestState() {
        var source = source(KOSPI, "00", "N");
        var first = parser.parse(source);
        parser.parse(source(KisStockMasterMarket.KOSDAQ, "03", "Y"));
        assertThat(parser.parse(source)).isEqualTo(first);
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(NullPointerException.class);
    }
}
