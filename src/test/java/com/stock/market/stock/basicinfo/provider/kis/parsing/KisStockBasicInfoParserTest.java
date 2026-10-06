package com.stock.market.stock.basicinfo.provider.kis.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.FIELDS;
import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.MESSAGE;
import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.content;
import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.json;
import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.output;
import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.rawRecord;
import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.root;
import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.values;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockBasicInfoParserTest {
    private final KisStockBasicInfoParser parser = new KisStockBasicInfoParser();

    @Test
    void parsesSelectedStringsAndEnvelopeWithOriginalInputHashAndVersion() throws Exception {
        byte[] input = content(output());
        byte[] before = input.clone();

        var result = parser.parse(input);

        assertThat(result.rawRecord()).isEqualTo(rawRecord(values()));
        assertThat(result.rawRecord().productNumber()).isEqualTo("00000A0004Y0");
        assertThat(result.responseCode()).isEqualTo("0");
        assertThat(result.messageCode()).isEqualTo("KIOK0530");
        assertThat(result.message()).isEqualTo(MESSAGE);
        assertThat(result.inputSha256()).isEqualTo(sha256(before));
        assertThat(result.parserVersion()).isEqualTo(KisStockBasicInfoParser.PARSER_VERSION);
        assertThat(input).isEqualTo(before);
        assertThat(parser.parse(input)).isEqualTo(result);
        Arrays.fill(input, (byte) 0);
        assertThat(result.rawRecord()).isEqualTo(rawRecord(values()));
        assertThat(result.inputSha256()).isEqualTo(sha256(before));
    }

    @ParameterizedTest
    @MethodSource("uninterpretedStrings")
    void preservesEveryBlankAndUnknownOutputStringWithoutNormalization(String field, String value) {
        String[] expected = values();
        expected[FIELDS.indexOf(field)] = value;

        assertThat(parser.parse(content(output().put(field, value))).rawRecord()).isEqualTo(rawRecord(expected));
    }

    @ParameterizedTest
    @MethodSource("rawMessages")
    void preservesBlankUnknownAndWhitespaceEnvelopeMessages(String field, String value) {
        var root = root().put(field, value);
        var result = parser.parse(json(root.toString()));

        assertThat(field.equals("msg_cd") ? result.messageCode() : result.message()).isEqualTo(value);
    }

    @Test
    void doesNotTreatCommonStockCodeOrEmptyDelistingDateAsEligibility() {
        var output = output().put("scty_grp_id_cd", "ST").put("stck_kind_cd", "101")
                .put("prdt_name", "SYNTHETIC SPAC").put("tr_stop_yn", "Y").put("admn_item_yn", "Y")
                .put("nxt_tr_stop_yn", "N").put("cptt_trad_tr_psbl_yn", "N");

        var record = parser.parse(content(output)).rawRecord();

        assertThat(record.name()).isEqualTo("SYNTHETIC SPAC");
        assertThat(record.rawStockKind()).isEqualTo("101");
        assertThat(record.rawSuspension()).isEqualTo("Y");
        assertThat(record.rawManagement()).isEqualTo("Y");
        assertThat(record.rawDelistingDate()).isEmpty();
        assertThat(record.rawNxtSuspension()).isEqualTo("N");
        assertThat(record.rawCompetitiveTradingPermission()).isEqualTo("N");
    }

    @Test
    void allowsUnselectedFieldsButIncludesThemInOriginalByteHash() throws Exception {
        byte[] original = content(output());
        var extended = root();
        extended.put("future_envelope_field", "OPAQUE");
        ((ObjectNode) extended.get("output")).put("etf_dvsn_cd", "0");
        ((ObjectNode) extended.get("output")).putObject("future_field").put("value", "UNKNOWN");
        byte[] input = json(extended.toString());

        var result = parser.parse(input);

        assertThat(result.rawRecord()).isEqualTo(parser.parse(original).rawRecord());
        assertThat(result.inputSha256()).isEqualTo(sha256(input)).isNotEqualTo(sha256(original));
    }

    @Test
    void hashesOriginalFormattingWhileDecodingEscapesToTheSameValues() throws Exception {
        String compact = root().toString();
        byte[] formatted = json(" \n" + compact.replace("0004Y0", "\\u0030\\u0030\\u0030\\u0034Y0") + "\n ");

        var result = parser.parse(formatted);
        var compactResult = parser.parse(json(compact));

        assertThat(result.rawRecord()).isEqualTo(compactResult.rawRecord());
        assertThat(result.message()).isEqualTo(compactResult.message());
        assertThat(result.inputSha256()).isEqualTo(sha256(formatted)).isNotEqualTo(compactResult.inputSha256());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "null", "[]", "1", "true", "\"error\"", "{}"})
    void rejectsNonObjectOrIncompleteRoots(String input) {
        assertThatThrownBy(() -> parser.parse(json(input))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "1", "true", "\"output\"", "{}"})
    void rejectsMissingOrNonObjectOutputInsteadOfReturningAnEmptyResult(String invalidOutput) {
        String input = "{\"rt_cd\":\"0\",\"msg_cd\":\"OK\",\"msg1\":\"OK\",\"output\":" + invalidOutput + "}";

        assertThatThrownBy(() -> parser.parse(json(input))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingOutput() {
        var root = root();
        root.remove("output");

        assertThatThrownBy(() -> parser.parse(json(root.toString())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("output");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "", " ", "00", "0 ", " 0", "DO_NOT_ECHO_RESPONSE_CODE"})
    void rejectsUnsuccessfulOrNormalizedResponseCodes(String responseCode) {
        var root = root().put("rt_cd", responseCode).put("msg1", "DO_NOT_ECHO_RESPONSE_MESSAGE");

        assertThatThrownBy(() -> parser.parse(json(root.toString())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rt_cd")
                .hasMessageNotContaining("DO_NOT_ECHO").hasNoCause();
    }

    @ParameterizedTest
    @MethodSource("stringFields")
    void rejectsEveryMissingSelectedFieldInsteadOfFillingDefaults(String field) {
        var root = root();
        objectContaining(root, field).remove(field);

        assertThatThrownBy(() -> parser.parse(json(root.toString())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("field=" + field);
    }

    @ParameterizedTest
    @MethodSource("nonStringValues")
    void rejectsNullAndNonStringValuesWithoutCoercion(String field, JsonNode value) {
        var root = root();
        objectContaining(root, field).set(field, value);

        assertThatThrownBy(() -> parser.parse(json(root.toString())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("field=" + field);
    }

    @Test
    void rejectsDuplicateKeysIncludingEscapedAndUnselectedKeys() {
        String duplicate = "{\"pdno\":\"DO_NOT_ECHO\"," + output().toString().substring(1);
        String escaped = "{\"\\u0070dno\":\"DO_NOT_ECHO\"," + output().toString().substring(1);
        String envelope = "{\"rt_cd\":\"0\",\"msg_cd\":\"OK\",\"msg1\":\"OK\",\"output\":";
        for (String input : new String[]{"{\"rt_cd\":\"0\"," + root().toString().substring(1),
                envelope + duplicate + "}", envelope + escaped + "}",
                "{\"future\":{\"unknown\":\"A\",\"unknown\":\"B\"}," + root().toString().substring(1)}) {
            assertThatThrownBy(() -> parser.parse(json(input)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid JSON content.")
                    .hasMessageNotContaining("DO_NOT_ECHO").hasNoCause();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {" {}", " null", " true", " trailing", " /* comment */"})
    void rejectsTrailingTokens(String suffix) {
        assertThatThrownBy(() -> parser.parse(json(root() + suffix)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid JSON content.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"output\":[}", "{\"output\":DO_NOT_ECHO_RESPONSE_VALUES}"})
    void rejectsMalformedJsonWithoutExposingRawMessagesOrCauses(String input) {
        assertThatThrownBy(() -> parser.parse(json(input)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid JSON content.")
                .hasMessageNotContaining("DO_NOT_ECHO_RESPONSE_VALUES").hasNoCause();
    }

    @Test
    void rejectsInvalidUtf8InsideString() {
        byte[] input = json(root().toString());
        int offset = new String(input, StandardCharsets.ISO_8859_1).indexOf("00000A");
        input[offset] = (byte) 0xff;

        assertThatThrownBy(() -> parser.parse(input))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid JSON content.").hasNoCause();
    }

    @Test
    void rejectsNullEmptyAndOversizedInput() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> parser.parse(new byte[0])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(new byte[KisStockBasicInfoParser.MAX_CONTENT_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 MiB");
    }

    @Test
    void acceptsExactlyTheSizeLimit() throws Exception {
        byte[] input = new byte[KisStockBasicInfoParser.MAX_CONTENT_BYTES];
        Arrays.fill(input, (byte) ' ');
        byte[] payload = content(output());
        System.arraycopy(payload, 0, input, 0, payload.length);

        var result = parser.parse(input);

        assertThat(result.rawRecord()).isEqualTo(rawRecord(values()));
        assertThat(result.inputSha256()).isEqualTo(sha256(input));
    }

    private static ObjectNode objectContaining(ObjectNode root, String field) {
        return FIELDS.contains(field) ? (ObjectNode) root.get("output") : root;
    }

    private static Stream<String> stringFields() {
        return Stream.concat(Stream.of("rt_cd", "msg_cd", "msg1"), FIELDS.stream());
    }

    private static Stream<Arguments> uninterpretedStrings() {
        return FIELDS.stream().flatMap(field -> Stream.of("", " \t\r\n ", "__", "UNVERIFIED_NEW_VALUE")
                .map(value -> Arguments.of(field, value)));
    }

    private static Stream<Arguments> rawMessages() {
        return Stream.of("msg_cd", "msg1").flatMap(field -> Stream.of("", " \t\r\n ", "UNVERIFIED_NEW_VALUE")
                .map(value -> Arguments.of(field, value)));
    }

    private static Stream<Arguments> nonStringValues() {
        return stringFields().flatMap(field -> Stream.<JsonNode>of(NullNode.instance, IntNode.valueOf(0),
                        BooleanNode.TRUE, JsonNodeFactory.instance.arrayNode(), JsonNodeFactory.instance.objectNode())
                .map(value -> Arguments.of(field, value)));
    }

    private static String sha256(byte[] input) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
    }
}
