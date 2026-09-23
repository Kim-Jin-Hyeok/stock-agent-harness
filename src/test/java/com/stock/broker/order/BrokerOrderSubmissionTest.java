package com.stock.broker.order;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerOrderSubmissionTest {
    private static final Instant SUBMITTED_AT = Instant.parse("2026-09-23T00:00:00Z");

    @Test
    void createsAcceptedSubmissionWithBrokerOrderId() {
        BrokerOrderSubmission submission = new BrokerOrderSubmission(
                BrokerOrderSubmissionStatus.ACCEPTED,
                "0000123456",
                SUBMITTED_AT,
                null
        );

        assertThat(submission.status()).isEqualTo(BrokerOrderSubmissionStatus.ACCEPTED);
        assertThat(submission.brokerOrderId()).isEqualTo("0000123456");
        assertThat(submission.submittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(submission.reason()).isNull();
    }

    @Test
    void createsRejectedSubmissionWithReason() {
        BrokerOrderSubmission submission = new BrokerOrderSubmission(
                BrokerOrderSubmissionStatus.REJECTED,
                null,
                SUBMITTED_AT,
                "Order quantity exceeds the broker limit."
        );

        assertThat(submission.status()).isEqualTo(BrokerOrderSubmissionStatus.REJECTED);
        assertThat(submission.brokerOrderId()).isNull();
        assertThat(submission.reason()).isEqualTo(
                "Order quantity exceeds the broker limit."
        );
    }

    @Test
    void rejectsNullStatus() {
        assertThatNullPointerException()
                .isThrownBy(() -> new BrokerOrderSubmission(
                        null,
                        "0000123456",
                        SUBMITTED_AT,
                        null
                ))
                .withMessage("status must not be null.");
    }

    @Test
    void rejectsNullSubmittedAt() {
        assertThatNullPointerException()
                .isThrownBy(() -> new BrokerOrderSubmission(
                        BrokerOrderSubmissionStatus.ACCEPTED,
                        "0000123456",
                        null,
                        null
                ))
                .withMessage("submittedAt must not be null.");
    }

    @Test
    void rejectsAcceptedSubmissionWithoutBrokerOrderId() {
        assertThatThrownBy(() -> new BrokerOrderSubmission(
                BrokerOrderSubmissionStatus.ACCEPTED,
                " ",
                SUBMITTED_AT,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "brokerOrderId must not be blank when submission is accepted."
                );
    }

    @Test
    void rejectsRejectedSubmissionWithoutReason() {
        assertThatThrownBy(() -> new BrokerOrderSubmission(
                BrokerOrderSubmissionStatus.REJECTED,
                null,
                SUBMITTED_AT,
                " "
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "reason must not be blank when submission is rejected."
                );
    }
}
