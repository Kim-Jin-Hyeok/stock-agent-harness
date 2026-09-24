package com.stock.broker.order.inquiry;

import com.stock.broker.order.BrokerOrderReference;
import com.stock.broker.order.BrokerOrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerOrderExecutionSnapshotTest {
    private static final BrokerOrderReference REFERENCE =
            new BrokerOrderReference("0000123456", "06010");

    @Test
    void createsPartiallyFilledSnapshot() {
        BrokerOrderExecutionSnapshot snapshot = snapshot(
                3L,
                209_700L,
                69_900L,
                BrokerOrderStatus.PARTIALLY_FILLED,
                null
        );

        assertThat(snapshot.requestedQuantity()).isEqualTo(10L);
        assertThat(snapshot.cumulativeFilledQuantity()).isEqualTo(3L);
        assertThat(snapshot.cumulativeFilledAmountKrw()).isEqualTo(209_700L);
        assertThat(snapshot.averageFilledPriceKrw()).isEqualTo(69_900L);
        assertThat(snapshot.status())
                .isEqualTo(BrokerOrderStatus.PARTIALLY_FILLED);
    }

    @Test
    void rejectsFilledQuantityGreaterThanRequestedQuantity() {
        assertThatThrownBy(() -> snapshot(
                11L,
                770_000L,
                70_000L,
                BrokerOrderStatus.FILLED,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "cumulativeFilledQuantity must be between 0 "
                                + "and requestedQuantity."
                );
    }

    @Test
    void rejectsAveragePriceWithoutFilledQuantity() {
        assertThatThrownBy(() -> snapshot(
                0L,
                0L,
                70_000L,
                BrokerOrderStatus.PENDING,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "averageFilledPriceKrw must be null when nothing is filled."
                );
    }

    @Test
    void rejectsMissingAveragePriceForFilledQuantity() {
        assertThatThrownBy(() -> snapshot(
                3L,
                209_700L,
                null,
                BrokerOrderStatus.PARTIALLY_FILLED,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "averageFilledPriceKrw must be positive when an order is filled."
                );
    }

    @Test
    void rejectsStatusThatDoesNotMatchFilledQuantity() {
        assertThatThrownBy(() -> snapshot(
                3L,
                209_700L,
                69_900L,
                BrokerOrderStatus.PENDING,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PENDING order must not have a filled quantity.");
    }

    @Test
    void rejectedSnapshotRequiresReason() {
        assertThatThrownBy(() -> snapshot(
                0L,
                0L,
                null,
                BrokerOrderStatus.REJECTED,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reason must not be blank for a rejected order.");
    }

    @Test
    void rejectsFilledAmountWithoutFilledQuantity() {
        assertThatThrownBy(() -> snapshot(
                0L,
                1L,
                null,
                BrokerOrderStatus.PENDING,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "cumulativeFilledAmountKrw must be zero when nothing is filled."
                );
    }

    @Test
    void rejectsMissingFilledAmountForFilledQuantity() {
        assertThatThrownBy(() -> snapshot(
                3L,
                0L,
                69_900L,
                BrokerOrderStatus.PARTIALLY_FILLED,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "cumulativeFilledAmountKrw must be positive when an order is filled."
                );
    }

    private BrokerOrderExecutionSnapshot snapshot(
            long cumulativeFilledQuantity,
            long cumulativeFilledAmountKrw,
            Long averageFilledPriceKrw,
            BrokerOrderStatus status,
            String reason
    ) {
        return new BrokerOrderExecutionSnapshot(
                REFERENCE,
                10L,
                cumulativeFilledQuantity,
                cumulativeFilledAmountKrw,
                averageFilledPriceKrw,
                status,
                reason
        );
    }
}
