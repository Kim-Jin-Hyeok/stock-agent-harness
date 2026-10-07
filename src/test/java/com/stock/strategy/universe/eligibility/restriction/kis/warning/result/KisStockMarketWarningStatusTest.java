package com.stock.strategy.universe.eligibility.restriction.kis.warning.result;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockMarketWarningStatusTest {
    @ParameterizedTest
    @CsvSource({"00,NO_WARNING_OBSERVED", "01,INVESTMENT_CAUTION_OBSERVED",
            "02,INVESTMENT_WARNING_OBSERVED", "03,INVESTMENT_RISK_OBSERVED"})
    void interpretsOnlyExactVerifiedCodes(String raw, KisStockMarketWarningStatus expected) {
        assertThat(KisStockMarketWarningStatus.fromRawValue(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"  ", "0 ", " 0", "99", "??", "ab", "0", "000", " 00", "00 "})
    void leavesUnknownAndNonLiteralCodesUnverified(String raw) {
        assertThat(KisStockMarketWarningStatus.fromRawValue(raw)).isEqualTo(KisStockMarketWarningStatus.VALUE_UNVERIFIED);
    }

    @Test
    void doesNotTreatMissingCodeAsNoWarning() {
        assertThatThrownBy(() -> KisStockMarketWarningStatus.fromRawValue(null)).isInstanceOf(NullPointerException.class);
    }
}
