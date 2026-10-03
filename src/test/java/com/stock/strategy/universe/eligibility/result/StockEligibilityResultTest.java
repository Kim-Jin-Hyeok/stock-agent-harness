package com.stock.strategy.universe.eligibility.result;

import com.stock.strategy.universe.eligibility.input.StockEligibilityInput;
import com.stock.strategy.universe.eligibility.input.StockMarket;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import com.stock.strategy.universe.eligibility.request.StockEligibilityRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockEligibilityResultTest {
    private final StockEligibilityRequest request = new StockEligibilityRequest(
            LocalDate.of(2026, 9, 23), Instant.parse("2026-09-23T09:00:00Z"),
            Set.of(StockMarket.KOSPI), Set.of(StockSecurityType.COMMON_STOCK)
    );
    private final StockEligibilityInput input = new StockEligibilityInput(
            "005930", null, null, null, null, null, null, null
    );

    @Test
    void preservesRequestAndMissingInputWithoutDroppingTheSymbol() {
        StockEligibilityResult result = new StockEligibilityResult(
                request, input, StockEligibilityStatus.DATA_UNVERIFIED,
                StockEligibilityReasonCode.AS_OF_DATE_UNVERIFIED
        );

        assertThat(result.request()).isSameAs(request);
        assertThat(result.input()).isSameAs(input);
        assertThat(result.input().symbol()).isEqualTo("005930");
        assertThat(result.status()).isEqualTo(StockEligibilityStatus.DATA_UNVERIFIED);
        assertThat(result.reasonCode()).isEqualTo(StockEligibilityReasonCode.AS_OF_DATE_UNVERIFIED);
    }

    @ParameterizedTest
    @EnumSource(StockEligibilityReasonCode.class)
    void rejectsStatusThatContradictsReason(StockEligibilityReasonCode reason) {
        StockEligibilityStatus wrongStatus = reason.status() == StockEligibilityStatus.ELIGIBLE
                ? StockEligibilityStatus.INELIGIBLE : StockEligibilityStatus.ELIGIBLE;

        assertThatThrownBy(() -> new StockEligibilityResult(request, input, wrongStatus, reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("status must match reasonCode status.");
    }

    @Test
    void rejectsMissingResultFields() {
        StockEligibilityReasonCode reason = StockEligibilityReasonCode.AS_OF_DATE_UNVERIFIED;
        StockEligibilityStatus status = StockEligibilityStatus.DATA_UNVERIFIED;

        assertThatThrownBy(() -> new StockEligibilityResult(null, input, status, reason))
                .isInstanceOf(NullPointerException.class).hasMessage("request must not be null.");
        assertThatThrownBy(() -> new StockEligibilityResult(request, null, status, reason))
                .isInstanceOf(NullPointerException.class).hasMessage("input must not be null.");
        assertThatThrownBy(() -> new StockEligibilityResult(request, input, null, reason))
                .isInstanceOf(NullPointerException.class).hasMessage("status must not be null.");
        assertThatThrownBy(() -> new StockEligibilityResult(request, input, status, null))
                .isInstanceOf(NullPointerException.class).hasMessage("reasonCode must not be null.");
    }
}
