package com.stock.broker.order.cancellation;

import com.stock.broker.order.BrokerOrderReference;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerOrderCancellationSubmissionTest {
    private static final Instant SUBMITTED_AT =
            Instant.parse("2026-09-24T00:00:00Z");

    @Test
    void createsAcceptedCancellationSubmission() {
        BrokerOrderReference cancellationReference = cancellationReference();

        BrokerOrderCancellationSubmission submission =
                new BrokerOrderCancellationSubmission(
                        BrokerOrderCancellationSubmissionStatus.ACCEPTED,
                        cancellationReference,
                        SUBMITTED_AT,
                        null
                );

        assertThat(submission.status())
                .isEqualTo(BrokerOrderCancellationSubmissionStatus.ACCEPTED);
        assertThat(submission.cancellationReference())
                .isEqualTo(cancellationReference);
        assertThat(submission.submittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(submission.reason()).isNull();
    }

    @Test
    void createsRejectedCancellationSubmission() {
        BrokerOrderCancellationSubmission submission =
                new BrokerOrderCancellationSubmission(
                        BrokerOrderCancellationSubmissionStatus.REJECTED,
                        null,
                        SUBMITTED_AT,
                        "The order cannot be canceled."
                );

        assertThat(submission.status())
                .isEqualTo(BrokerOrderCancellationSubmissionStatus.REJECTED);
        assertThat(submission.cancellationReference()).isNull();
        assertThat(submission.reason())
                .isEqualTo("The order cannot be canceled.");
    }

    @Test
    void rejectsNullStatus() {
        assertThatNullPointerException()
                .isThrownBy(() -> new BrokerOrderCancellationSubmission(
                        null,
                        cancellationReference(),
                        SUBMITTED_AT,
                        null
                ))
                .withMessage("status must not be null.");
    }

    @Test
    void rejectsNullSubmittedAt() {
        assertThatNullPointerException()
                .isThrownBy(() -> new BrokerOrderCancellationSubmission(
                        BrokerOrderCancellationSubmissionStatus.ACCEPTED,
                        cancellationReference(),
                        null,
                        null
                ))
                .withMessage("submittedAt must not be null.");
    }

    @Test
    void rejectsAcceptedSubmissionWithoutCancellationReference() {
        assertThatThrownBy(() -> new BrokerOrderCancellationSubmission(
                BrokerOrderCancellationSubmissionStatus.ACCEPTED,
                null,
                SUBMITTED_AT,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "cancellationReference must not be null when "
                                + "cancellation is accepted."
                );
    }

    @Test
    void rejectsAcceptedSubmissionWithReason() {
        assertThatThrownBy(() -> new BrokerOrderCancellationSubmission(
                BrokerOrderCancellationSubmissionStatus.ACCEPTED,
                cancellationReference(),
                SUBMITTED_AT,
                "Unexpected reason."
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "reason must be null when cancellation is accepted."
                );
    }

    @Test
    void rejectsRejectedSubmissionWithCancellationReference() {
        assertThatThrownBy(() -> new BrokerOrderCancellationSubmission(
                BrokerOrderCancellationSubmissionStatus.REJECTED,
                cancellationReference(),
                SUBMITTED_AT,
                "The order cannot be canceled."
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "cancellationReference must be null when cancellation "
                                + "is rejected."
                );
    }

    @Test
    void rejectsRejectedSubmissionWithoutReason() {
        assertThatThrownBy(() -> new BrokerOrderCancellationSubmission(
                BrokerOrderCancellationSubmissionStatus.REJECTED,
                null,
                SUBMITTED_AT,
                " "
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "reason must not be blank when cancellation is rejected."
                );
    }

    private BrokerOrderReference cancellationReference() {
        return new BrokerOrderReference("0000123457", "06010");
    }
}
