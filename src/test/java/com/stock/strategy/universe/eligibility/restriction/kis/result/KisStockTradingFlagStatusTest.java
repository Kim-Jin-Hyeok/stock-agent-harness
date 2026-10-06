package com.stock.strategy.universe.eligibility.restriction.kis.result;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class KisStockTradingFlagStatusTest {
    @ParameterizedTest
    @MethodSource("values")
    void observesOnlyExactLiteralValuesWithoutNormalization(String raw, KisStockTradingFlagStatus expected) {
        assertThat(KisStockTradingFlagStatus.fromRawValue(raw)).isEqualTo(expected);
    }

    static Stream<Arguments> values() {
        return Stream.concat(Stream.of(Arguments.of(null, KisStockTradingFlagStatus.FIELD_NOT_PROVIDED),
                        Arguments.of("Y", KisStockTradingFlagStatus.Y_OBSERVED), Arguments.of("N", KisStockTradingFlagStatus.N_OBSERVED)),
                Stream.of("", " ", "\t", "y", "n", " Y", "Y ", "YES", "0", "1", "?")
                        .map(raw -> Arguments.of(raw, KisStockTradingFlagStatus.VALUE_UNVERIFIED)));
    }
}
