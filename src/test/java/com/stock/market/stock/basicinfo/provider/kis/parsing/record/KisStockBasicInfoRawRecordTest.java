package com.stock.market.stock.basicinfo.provider.kis.parsing.record;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.FIELDS;
import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.rawRecord;
import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.values;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockBasicInfoRawRecordTest {
    @Test
    void retainsValuesWithoutKeepingTheConstructorInputArray() {
        String[] input = values();
        var record = rawRecord(input);
        input[0] = "CHANGED";

        assertThat(record).isEqualTo(rawRecord(values()));
        assertThat(record.productNumber()).isEqualTo("00000A0004Y0");
    }

    @ParameterizedTest
    @MethodSource("fieldIndexes")
    void rejectsNullForEveryRawField(int index) {
        String[] input = values();
        input[index] = null;

        assertThatThrownBy(() -> rawRecord(input)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @MethodSource("uninterpretedValues")
    void acceptsBlankWhitespaceUnknownAndUnparsedDateStrings(int index, String value) {
        String[] input = values();
        input[index] = value;

        assertThatCode(() -> rawRecord(input)).doesNotThrowAnyException();
    }

    private static IntStream fieldIndexes() {
        return IntStream.range(0, FIELDS.size());
    }

    private static Stream<Arguments> uninterpretedValues() {
        return fieldIndexes().boxed().flatMap(index -> Stream.of("", " \t ", "UNKNOWN_CODE", "NOT_A_DATE")
                .map(value -> Arguments.of(index, value)));
    }
}
