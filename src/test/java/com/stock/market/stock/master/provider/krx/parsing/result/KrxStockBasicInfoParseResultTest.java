package com.stock.market.stock.master.provider.krx.parsing.result;

import com.stock.market.stock.master.provider.krx.parsing.KrxStockBasicInfoParser;
import com.stock.market.stock.master.provider.krx.parsing.record.KrxStockBasicInfoRawRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.rawRecord;
import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.values;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KrxStockBasicInfoParseResultTest {
    private static final String HASH = "a".repeat(64);

    @Test
    void defensivelyCopiesRecordsAndRetainsOrderAndDuplicates() {
        var first = rawRecord(1, values());
        var second = rawRecord(2, values());
        var records = new ArrayList<>(List.of(first, second));
        var result = result(records);
        records.clear();

        assertThat(result.records()).containsExactly(first, second);
        assertThatThrownBy(() -> result.records().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(result.inputSha256()).isEqualTo(HASH);
        assertThat(result.parserVersion()).isEqualTo(KrxStockBasicInfoParser.PARSER_VERSION);
    }

    @Test
    void allowsEmptyRecordsWithoutCompletenessOrEligibilityAssertions() {
        assertThat(result(List.of()).records()).isEmpty();
    }

    @Test
    void rejectsNullListAndNullElements() {
        assertThatThrownBy(() -> result(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> result(Arrays.asList(rawRecord(1, values()), null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsMissingRepeatedSkippedAndReorderedRowNumbers() {
        var first = rawRecord(1, values());
        var second = rawRecord(2, values());
        var third = rawRecord(3, values());
        for (var records : List.of(List.of(second), List.of(first, first), List.of(first, third), List.of(second, first))) {
            assertThatThrownBy(() -> result(records)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"bad", " ", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsMissingOrInvalidLowercaseSha256(String hash) {
        assertThatThrownBy(() -> new KrxStockBasicInfoParseResult(hash, KrxStockBasicInfoParser.PARSER_VERSION, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n"})
    void rejectsMissingOrBlankParserVersion(String version) {
        assertThatThrownBy(() -> new KrxStockBasicInfoParseResult(HASH, version, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static KrxStockBasicInfoParseResult result(List<KrxStockBasicInfoRawRecord> records) {
        return new KrxStockBasicInfoParseResult(HASH, KrxStockBasicInfoParser.PARSER_VERSION, records);
    }
}
