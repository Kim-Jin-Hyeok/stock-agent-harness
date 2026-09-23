package com.stock.broker.order;

import java.time.Instant;
import java.util.Objects;

public record BrokerOrderSubmission(
        BrokerOrderSubmissionStatus status,
        BrokerOrderReference reference,
        Instant submittedAt,
        String reason
) {
    public BrokerOrderSubmission {
        Objects.requireNonNull(status, "status must not be null.");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null.");

        if (status == BrokerOrderSubmissionStatus.ACCEPTED && reference == null) {
            throw new IllegalArgumentException(
                    "reference must not be null when submission is accepted."
            );
        }
        if (status == BrokerOrderSubmissionStatus.REJECTED
                && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException(
                    "reason must not be blank when submission is rejected."
            );
        }
    }
}
