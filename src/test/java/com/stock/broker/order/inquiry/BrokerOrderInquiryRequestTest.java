package com.stock.broker.order.inquiry;

import com.stock.broker.order.BrokerOrderReference;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerOrderInquiryRequestTest {
    private static final BrokerOrderReference REFERENCE =
            new BrokerOrderReference("0000123456", "06010");
    private static final Instant SUBMITTED_AT =
            Instant.parse("2026-09-23T00:00:00Z");

    @Test
    void createsInquiryRequest() {
        BrokerOrderInquiryRequest request = new BrokerOrderInquiryRequest(
                REFERENCE,
                "005930",
                SUBMITTED_AT
        );

        assertThat(request.reference()).isEqualTo(REFERENCE);
        assertThat(request.symbol()).isEqualTo("005930");
        assertThat(request.submittedAt()).isEqualTo(SUBMITTED_AT);
    }

    @Test
    void rejectsMissingReference() {
        assertThatNullPointerException()
                .isThrownBy(() -> new BrokerOrderInquiryRequest(
                        null,
                        "005930",
                        SUBMITTED_AT
                ))
                .withMessage("reference must not be null.");
    }

    @Test
    void rejectsBlankSymbol() {
        assertThatThrownBy(() -> new BrokerOrderInquiryRequest(
                REFERENCE,
                " ",
                SUBMITTED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must not be blank.");
    }

    @Test
    void rejectsMissingSubmittedAt() {
        assertThatNullPointerException()
                .isThrownBy(() -> new BrokerOrderInquiryRequest(
                        REFERENCE,
                        "005930",
                        null
                ))
                .withMessage("submittedAt must not be null.");
    }
}
