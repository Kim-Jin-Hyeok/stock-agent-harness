package com.stock.market.stock.master.provider.krx.parsing.record;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.rawRecord;
import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.values;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KrxStockBasicInfoRawRecordTest {
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "__", "UNKNOWN"})
    void retainsUninterpretedStringsIncludingBlankIdentifiers(String value) {
        String[] values = new String[12];
        Arrays.fill(values, value);
        var record = rawRecord(1, values);

        assertThat(record.standardCode()).isEqualTo(value);
        assertThat(record.symbol()).isEqualTo(value);
        assertThat(record.rawListingDate()).isEqualTo(value);
        assertThat(record.rawMarket()).isEqualTo(value);
        assertThat(record.rawStockKind()).isEqualTo(value);
        assertThat(record.rawParValue()).isEqualTo(value);
        assertThat(record.rawListedShares()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11})
    void rejectsNullInEveryRawStringField(int index) {
        String[] values = values();
        values[index] = null;

        assertThatThrownBy(() -> rawRecord(1, values)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiresPositiveSourceRowNumber() {
        assertThatThrownBy(() -> rawRecord(0, values())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rawRecord(-1, values())).isInstanceOf(IllegalArgumentException.class);
        assertThat(rawRecord(Integer.MAX_VALUE, values()).rowNumber()).isEqualTo(Integer.MAX_VALUE);
    }
}
