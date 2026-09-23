package com.stock.broker.order.inquiry;

import java.time.Instant;
import java.util.Objects;

public record BrokerOrderInquiryResult(
        BrokerOrderInquiryStatus status,
        BrokerOrderExecutionSnapshot snapshot,
        Instant observedAt
) {
    public BrokerOrderInquiryResult {
        Objects.requireNonNull(status, "status must not be null.");
        Objects.requireNonNull(observedAt, "observedAt must not be null.");

        if (status == BrokerOrderInquiryStatus.FOUND && snapshot == null) {
            throw new IllegalArgumentException(
                    "snapshot must not be null when an order is found."
            );
        }
        if (status == BrokerOrderInquiryStatus.NOT_FOUND && snapshot != null) {
            throw new IllegalArgumentException(
                    "snapshot must be null when an order is not found."
            );
        }
    }

    public static BrokerOrderInquiryResult found(
            BrokerOrderExecutionSnapshot snapshot,
            Instant observedAt
    ) {
        return new BrokerOrderInquiryResult(
                BrokerOrderInquiryStatus.FOUND,
                snapshot,
                observedAt
        );
    }

    public static BrokerOrderInquiryResult notFound(Instant observedAt) {
        return new BrokerOrderInquiryResult(
                BrokerOrderInquiryStatus.NOT_FOUND,
                null,
                observedAt
        );
    }
}
