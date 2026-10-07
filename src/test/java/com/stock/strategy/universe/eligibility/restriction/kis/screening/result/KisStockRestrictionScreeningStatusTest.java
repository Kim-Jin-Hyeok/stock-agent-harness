package com.stock.strategy.universe.eligibility.restriction.kis.screening.result;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.List;

import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.NO_EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.REVIEW_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockRestrictionScreeningStatusTest {
    @ParameterizedTest
    @EnumSource(KisStockRestrictionScreeningReasonCode.class)
    void classifiesEachDiagnosticAndRetainsConnectionFailurePriority(KisStockRestrictionScreeningReasonCode reason) {
        assertThat(KisStockRestrictionScreeningStatus.fromReasonCodes(List.of(reason)))
                .isEqualTo(reason.isExclusionSignal() ? EXCLUSION_SIGNAL_OBSERVED : REVIEW_REQUIRED);
        var mixed = List.of(reason, KisStockRestrictionScreeningReasonCode.MARKET_WARNING_INVESTMENT_RISK_OBSERVED);
        assertThat(KisStockRestrictionScreeningStatus.fromReasonCodes(mixed))
                .isEqualTo(reason.isConnectionFailure() ? REVIEW_REQUIRED : EXCLUSION_SIGNAL_OBSERVED);
    }

    @Test
    void requiresAnEmptyListForNoSignalAndRejectsNullValues() {
        assertThat(KisStockRestrictionScreeningStatus.fromReasonCodes(List.of())).isEqualTo(NO_EXCLUSION_SIGNAL_OBSERVED);
        assertThatThrownBy(() -> KisStockRestrictionScreeningStatus.fromReasonCodes(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> KisStockRestrictionScreeningStatus.fromReasonCodes(Arrays.asList((KisStockRestrictionScreeningReasonCode) null)))
                .isInstanceOf(NullPointerException.class);
    }
}
