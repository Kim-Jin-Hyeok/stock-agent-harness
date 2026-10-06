package com.stock.market.stock.basicinfo.provider.kis.parsing.result;

import com.stock.market.stock.basicinfo.provider.kis.parsing.KisStockBasicInfoParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.MESSAGE;
import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.rawRecord;
import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.values;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockBasicInfoParseResultTest {
    private static final String HASH = "a".repeat(64);
    private static final String VERSION = KisStockBasicInfoParser.PARSER_VERSION;

    @Test
    void retainsRawRecordHashVersionAndUnmodifiedMessages() {
        var rawRecord = rawRecord(values());
        var result = new KisStockBasicInfoParseResult(HASH, VERSION, "0", "KIOK0530", MESSAGE, rawRecord);

        assertThat(result.rawRecord()).isSameAs(rawRecord);
        assertThat(result.inputSha256()).isEqualTo(HASH);
        assertThat(result.parserVersion()).isEqualTo(VERSION);
        assertThat(result.responseCode()).isEqualTo("0");
        assertThat(result.messageCode()).isEqualTo("KIOK0530");
        assertThat(result.message()).isEqualTo(MESSAGE);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "not-a-hash", "ABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFAB"})
    void rejectsInvalidInputHash(String hash) {
        assertThatThrownBy(() -> new KisStockBasicInfoParseResult(hash, VERSION, "0", "OK", "OK", rawRecord(values())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIncorrectHashLength() {
        for (int length : new int[]{63, 65}) {
            assertThatThrownBy(() -> new KisStockBasicInfoParseResult("a".repeat(length), VERSION,
                    "0", "OK", "OK", rawRecord(values()))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t\r\n"})
    void rejectsAbsentParserVersion(String version) {
        assertThatThrownBy(() -> new KisStockBasicInfoParseResult(HASH, version, "0", "OK", "OK", rawRecord(values())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "1", "00", "0 ", " 0"})
    void rejectsNonSuccessResponseCode(String code) {
        assertThatThrownBy(() -> new KisStockBasicInfoParseResult(HASH, VERSION, code, "OK", "OK", rawRecord(values())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullRecordOrMessages() {
        assertThatThrownBy(() -> new KisStockBasicInfoParseResult(HASH, VERSION, "0", "OK", "OK", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockBasicInfoParseResult(HASH, VERSION, "0", null, "OK", rawRecord(values())))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockBasicInfoParseResult(HASH, VERSION, "0", "OK", null, rawRecord(values())))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \t\r\n ", "UNVERIFIED_NEW_VALUE"})
    void permitsBlankAndUnknownMessagesWithoutTrimming(String value) {
        var result = new KisStockBasicInfoParseResult(HASH, VERSION, "0", value, value, rawRecord(values()));

        assertThat(result.messageCode()).isEqualTo(value);
        assertThat(result.message()).isEqualTo(value);
    }
}
