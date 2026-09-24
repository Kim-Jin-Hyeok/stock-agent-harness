package com.stock.broker.order.cancellation;

import com.stock.broker.order.BrokerOrderReference;

import java.time.Instant;
import java.util.Objects;

public record BrokerOrderCancellationSubmission(
        BrokerOrderCancellationSubmissionStatus status,
        BrokerOrderReference cancellationReference,
        Instant submittedAt,
        String reason
) {
    public BrokerOrderCancellationSubmission {
        Objects.requireNonNull(status, "status must not be null.");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null.");

        if (status == BrokerOrderCancellationSubmissionStatus.ACCEPTED) {
            if (cancellationReference == null) {
                throw new IllegalArgumentException(
                        "cancellationReference must not be null when "
                                + "cancellation is accepted."
                );
            }
            if (reason != null) {
                throw new IllegalArgumentException(
                        "reason must be null when cancellation is accepted."
                );
            }
        }

        if (status == BrokerOrderCancellationSubmissionStatus.REJECTED) {
            if (cancellationReference != null) {
                throw new IllegalArgumentException(
                        "cancellationReference must be null when cancellation "
                                + "is rejected."
                );
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "reason must not be blank when cancellation is rejected."
                );
            }
        }
    }
}
