package com.stock.market.stock.master.provider.krx.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Stream;

import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.FIELDS;
import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.NAME;
import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.content;
import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.json;
import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.rawRecord;
import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.row;
import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.values;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KrxStockBasicInfoParserTest {
    private final KrxStockBasicInfoParser parser = new KrxStockBasicInfoParser();

    @Test
    void parsesAllTwelveStringsAndRetainsOriginalInputHashAndVersion() throws Exception {
        byte[] input = content(row());
        byte[] before = input.clone();

        var result = parser.parse(input);
        var record = result.records().getFirst();

        assertThat(record.rowNumber()).isEqualTo(1);
        assertThat(record.standardCode()).isEqualTo("KR7005930003");
        assertThat(record.symbol()).isEqualTo("005930");
        assertThat(record.name()).isEqualTo(NAME);
        assertThat(record.abbreviatedName()).isEqualTo("SYNTHETIC");
        assertThat(record.englishName()).isEqualTo("SYNTHETIC STOCK");
        assertThat(record.rawListingDate()).isEqualTo("UNKNOWN_DATE");
        assertThat(record.rawMarket()).isEqualTo("UNVERIFIED_MARKET");
        assertThat(record.rawSecurityGroup()).isEqualTo("UNVERIFIED_GROUP");
        assertThat(record.rawSection()).isEqualTo("-");
        assertThat(record.rawStockKind()).isEqualTo("UNVERIFIED_KIND");
        assertThat(record.rawParValue()).isEqualTo("000100.00");
        assertThat(record.rawListedShares()).isEqualTo("001234");
        assertThat(result.records()).hasSize(1);
        assertThat(result.inputSha256()).isEqualTo(sha256(before));
        assertThat(result.parserVersion()).isEqualTo(KrxStockBasicInfoParser.PARSER_VERSION);
        assertThat(input).isEqualTo(before);
        assertThat(parser.parse(input)).isEqualTo(result);
        assertThatThrownBy(() -> result.records().clear()).isInstanceOf(UnsupportedOperationException.class);
        Arrays.fill(input, (byte) 0);
        assertThat(result.inputSha256()).isEqualTo(sha256(before));
        assertThat(result.records()).containsExactly(rawRecord(1, values()));
    }

    @Test
    void retainsSourceOrderAlphanumericCodesAndDuplicateIdentifiers() {
        var alpha = row().put("ISU_SRT_CD", "0001A0").put("ISU_CD", "KR70001A0001");
        var duplicate = row().put("ISU_NM", "DUPLICATE IDENTIFIER");
        var sameSymbol = row().put("ISU_CD", "DIFFERENT_STANDARD_CODE");
        var sameStandardCode = row().put("ISU_SRT_CD", "DIFFERENT_SYMBOL");

        var result = parser.parse(content(row(), alpha, duplicate, sameSymbol, sameStandardCode));

        assertThat(result.records()).extracting("rowNumber").containsExactly(1, 2, 3, 4, 5);
        assertThat(result.records()).extracting("symbol")
                .containsExactly("005930", "0001A0", "005930", "005930", "DIFFERENT_SYMBOL");
        assertThat(result.records()).extracting("standardCode")
                .containsExactly("KR7005930003", "KR70001A0001", "KR7005930003", "DIFFERENT_STANDARD_CODE", "KR7005930003");
        assertThat(result.records().get(2).name()).isEqualTo("DUPLICATE IDENTIFIER");
    }

    @ParameterizedTest
    @MethodSource("uninterpretedStrings")
    void preservesBlankAndUnknownStringValuesWithoutNormalization(String field, String value) {
        var row = row().put(field, value);
        String[] expected = values();
        expected[FIELDS.indexOf(field)] = value;

        assertThat(parser.parse(content(row)).records()).containsExactly(rawRecord(1, expected));
    }

    @Test
    void hashesOriginalFormattingButDecodesEscapedStringsToTheSameValues() throws Exception {
        String compact = new String(content(row()), StandardCharsets.UTF_8);
        byte[] formatted = json(" \n" + compact.replace("005930", "\\u0030\\u0030\\u0035\\u0039\\u0033\\u0030") + "\n ");

        var compactResult = parser.parse(json(compact));
        var formattedResult = parser.parse(formatted);

        assertThat(formattedResult.records()).isEqualTo(compactResult.records());
        assertThat(formattedResult.inputSha256()).isEqualTo(sha256(formatted));
        assertThat(formattedResult.inputSha256()).isNotEqualTo(compactResult.inputSha256());
    }

    @Test
    void retainsEmptyArrayAsAnEmptyParseResult() throws Exception {
        byte[] input = content();
        var result = parser.parse(input);

        assertThat(result.records()).isEmpty();
        assertThat(result.inputSha256()).isEqualTo(sha256(input));
        assertThat(result.parserVersion()).isEqualTo(KrxStockBasicInfoParser.PARSER_VERSION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "null", "[]", "123", "true", "\"error\"", "{}",
            "{\"message\":\"error\"}", "{\"OutBlock_1\":null}", "{\"OutBlock_1\":{}}",
            "{\"OutBlock_1\":\"[]\"}", "{\"OutBlock_1\":[],\"error\":\"FAILED\"}"})
    void rejectsInvalidRootAndArrayStructures(String input) {
        assertThatThrownBy(() -> parser.parse(json(input))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "1", "true", "\"row\"", "{}"})
    void rejectsInvalidRowsInsteadOfSkippingThem(String invalidRow) {
        String input = "{\"OutBlock_1\":[" + row() + "," + invalidRow + "," + row() + "]}";

        assertThatThrownBy(() -> parser.parse(json(input)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("row=2");
    }

    @ParameterizedTest
    @MethodSource("fields")
    void rejectsEveryMissingFieldWithoutFillingDefaults(String field) {
        var row = row();
        row.remove(field);

        assertThatThrownBy(() -> parser.parse(content(row)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("row=1");
    }

    @ParameterizedTest
    @MethodSource("nonStringValues")
    void rejectsNullAndNonStringValuesWithoutCoercion(String field, JsonNode value) {
        var row = row();
        row.set(field, value);

        assertThatThrownBy(() -> parser.parse(content(row))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("row=1").hasMessageContaining("field=" + field);
    }

    @Test
    void rejectsAdditionalFieldsAndReplacementOfKnownFieldsWithUnknownFields() {
        var extra = row().put("UNREVIEWED_FIELD", "RAW VALUE");
        var substituted = extra.deepCopy();
        substituted.remove("LIST_SHRS");

        assertThatThrownBy(() -> parser.parse(content(extra))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(content(substituted))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field=LIST_SHRS");
    }

    @Test
    void rejectsDuplicateJsonKeysAtRootAndRowIncludingEscapedDuplicates() {
        String duplicateRow = "{\"ISU_CD\":\"DO_NOT_ECHO\"," + row().toString().substring(1);
        String escapedDuplicateRow = "{\"\\u0049SU_CD\":\"DO_NOT_ECHO\"," + row().toString().substring(1);
        for (String input : new String[]{"{\"OutBlock_1\":[],\"OutBlock_1\":[]}",
                "{\"OutBlock_1\":[" + duplicateRow + "]}",
                "{\"OutBlock_1\":[" + escapedDuplicateRow + "]}"}) {
            assertThatThrownBy(() -> parser.parse(json(input)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid JSON content.");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"OutBlock_1\":[}", "{\"OutBlock_1\":[]} {}",
            "{\"OutBlock_1\":[]} null", "{\"OutBlock_1\":[]} true",
            "{\"OutBlock_1\":[]} trailing", "{\"OutBlock_1\":[]} /* comment */"})
    void rejectsMalformedJsonAndTrailingTokens(String input) {
        assertThatThrownBy(() -> parser.parse(json(input)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid JSON content.");
    }

    @Test
    void doesNotExposeRawJsonParserMessagesOrCauses() {
        assertThatThrownBy(() -> parser.parse(json("{\"OutBlock_1\":[DO_NOT_ECHO_RESPONSE_VALUES]}")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("DO_NOT_ECHO_RESPONSE_VALUES")
                .hasNoCause();
    }

    @Test
    void rejectsNullEmptyAndOversizedBytes() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> parser.parse(new byte[0])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(new byte[KrxStockBasicInfoParser.MAX_CONTENT_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("32 MiB");
    }

    @Test
    void acceptsExactlyTheContentSizeLimit() throws Exception {
        byte[] input = new byte[KrxStockBasicInfoParser.MAX_CONTENT_BYTES];
        Arrays.fill(input, (byte) ' ');
        byte[] payload = content(row());
        System.arraycopy(payload, 0, input, 0, payload.length);

        var result = parser.parse(input);

        assertThat(result.records()).containsExactly(rawRecord(1, values()));
        assertThat(result.inputSha256()).isEqualTo(sha256(input));
    }

    private static Stream<String> fields() {
        return FIELDS.stream();
    }

    private static Stream<Arguments> uninterpretedStrings() {
        return FIELDS.stream().flatMap(field -> Stream.of("", " \t\r\n ", "__", "UNVERIFIED_NEW_VALUE")
                .map(value -> Arguments.of(field, value)));
    }

    private static Stream<Arguments> nonStringValues() {
        return FIELDS.stream().flatMap(field -> Stream.<JsonNode>of(NullNode.instance, IntNode.valueOf(5930),
                        BooleanNode.TRUE, JsonNodeFactory.instance.arrayNode(), JsonNodeFactory.instance.objectNode())
                .map(value -> Arguments.of(field, value)));
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
