package com.stock.broker.order.inquiry;

import com.stock.broker.order.BrokerOrderReference;
import com.stock.broker.order.BrokerOrderStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerOrderInquiryResultTest {
    private static final Instant OBSERVED_AT =
            Instant.parse("2026-09-23T00:01:00Z");

    @Test
    void createsFoundResult() {
        BrokerOrderExecutionSnapshot snapshot = pendingSnapshot();

        BrokerOrderInquiryResult result = BrokerOrderInquiryResult.found(
                snapshot,
                OBSERVED_AT
        );

        assertThat(result.status()).isEqualTo(BrokerOrderInquiryStatus.FOUND);
        assertThat(result.snapshot()).isSameAs(snapshot);
        assertThat(result.observedAt()).isEqualTo(OBSERVED_AT);
    }

    @Test
    void createsNotFoundResultWithoutSnapshot() {
        BrokerOrderInquiryResult result =
                BrokerOrderInquiryResult.notFound(OBSERVED_AT);

        assertThat(result.status())
                .isEqualTo(BrokerOrderInquiryStatus.NOT_FOUND);
        assertThat(result.snapshot()).isNull();
        assertThat(result.observedAt()).isEqualTo(OBSERVED_AT);
    }

    @Test
    void foundResultRequiresSnapshot() {
        assertThatThrownBy(() -> new BrokerOrderInquiryResult(
                BrokerOrderInquiryStatus.FOUND,
                null,
                OBSERVED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "snapshot must not be null when an order is found."
                );
    }

    @Test
    void notFoundResultRejectsSnapshot() {
        assertThatThrownBy(() -> new BrokerOrderInquiryResult(
                BrokerOrderInquiryStatus.NOT_FOUND,
                pendingSnapshot(),
                OBSERVED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "snapshot must be null when an order is not found."
                );
    }

    @Test
    void resultRequiresObservedAt() {
        assertThatNullPointerException()
                .isThrownBy(() -> BrokerOrderInquiryResult.notFound(null))
                .withMessage("observedAt must not be null.");
    }

    private BrokerOrderExecutionSnapshot pendingSnapshot() {
        return new BrokerOrderExecutionSnapshot(
                new BrokerOrderReference("0000123456", "06010"),
                10L,
                0L,
                null,
                BrokerOrderStatus.PENDING,
                null
        );
    }
}
