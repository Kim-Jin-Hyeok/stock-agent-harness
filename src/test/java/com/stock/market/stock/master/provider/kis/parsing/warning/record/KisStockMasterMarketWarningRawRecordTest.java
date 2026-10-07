package com.stock.market.stock.master.provider.kis.parsing.warning.record;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockMasterMarketWarningRawRecordTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "000", "\tN", "\uac00N"})
    void rejectsMissingWrongWidthOrNonAsciiCode(String value) {
        assertThatThrownBy(() -> new KisStockMasterMarketWarningRawRecord(1, "005930", "KR7005930003", value, "N"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"YN", "\t", "\uac00"})
    void rejectsMissingWrongWidthOrNonAsciiPreannouncement(String value) {
        assertThatThrownBy(() -> new KisStockMasterMarketWarningRawRecord(1, "005930", "KR7005930003", "00", value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsInvalidLineNumber(int value) {
        assertThatThrownBy(() -> new KisStockMasterMarketWarningRawRecord(value, "005930", "KR7005930003", "00", "N"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingIdentifiers(String value) {
        assertThatThrownBy(() -> new KisStockMasterMarketWarningRawRecord(1, value, "KR7005930003", "00", "N"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KisStockMasterMarketWarningRawRecord(1, "005930", value, "00", "N"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
