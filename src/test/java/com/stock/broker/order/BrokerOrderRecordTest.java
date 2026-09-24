package com.stock.broker.order;

import com.stock.broker.order.cancellation.BrokerOrderCancellationSubmission;
import com.stock.broker.order.cancellation.BrokerOrderCancellationSubmissionStatus;
import com.stock.broker.order.inquiry.BrokerOrderExecutionSnapshot;
import com.stock.broker.order.inquiry.BrokerOrderInquiryResult;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerOrderRecordTest {
    private static final Instant SUBMITTED_AT = Instant.parse("2026-09-23T00:00:00Z");
    private static final Instant EXPIRES_AT = SUBMITTED_AT.plusSeconds(300);
    private static final Instant CANCELLATION_SUBMITTED_AT =
            SUBMITTED_AT.plusSeconds(10);

    @Test
    void createsPendingOrder() {
        BrokerOrderRecord order = pendingOrder();

        assertThat(order.status()).isEqualTo(BrokerOrderStatus.PENDING);
        assertThat(order.cumulativeFilledQuantity()).isZero();
        assertThat(order.averageFilledPriceKrw()).isNull();
        assertThat(order.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void createsRejectedOrderWithoutOrderReferenceAndExpiration() {
        BrokerOrderRecord order = order(
                null,
                0L,
                null,
                BrokerOrderStatus.REJECTED,
                "Broker rejected the order.",
                null
        );

        assertThat(order.reference()).isNull();
        assertThat(order.status()).isEqualTo(BrokerOrderStatus.REJECTED);
        assertThat(order.expiresAt()).isNull();
    }

    @Test
    void rejectsFilledQuantityGreaterThanRequestedQuantity() {
        assertThatThrownBy(() -> order(
                orderReference(),
                11L,
                70_000L,
                BrokerOrderStatus.FILLED,
                null,
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "cumulativeFilledQuantity must be between 0 and requestedQuantity."
                );
    }

    @Test
    void rejectsExpirationNotAfterSubmission() {
        assertThatThrownBy(() -> order(
                orderReference(),
                0L,
                null,
                BrokerOrderStatus.PENDING,
                null,
                SUBMITTED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expiresAt must be after submittedAt.");
    }

    @Test
    void rejectsMissingExpirationForSubmittedOrder() {
        assertThatNullPointerException()
                .isThrownBy(() -> order(
                        orderReference(),
                        0L,
                        null,
                        BrokerOrderStatus.PENDING,
                        null,
                        null
                ))
                .withMessage("expiresAt must not be null for a submitted order.");
    }

    @Test
    void recordsAcceptedCancellationWithoutChangingOrderStatus() {
        BrokerOrderCancellationSubmission submission = acceptedCancellation(
                CANCELLATION_SUBMITTED_AT
        );

        BrokerOrderRecord recorded = pendingOrder().recordCancellation(
                submission
        );

        assertThat(recorded.status()).isEqualTo(BrokerOrderStatus.PENDING);
        assertThat(recorded.cancellationSubmission()).isEqualTo(submission);
        assertThat(recorded.reference()).isEqualTo(orderReference());
    }

    @Test
    void recordsRejectedCancellationWithoutChangingOrderStatus() {
        BrokerOrderCancellationSubmission submission =
                rejectedCancellation(CANCELLATION_SUBMITTED_AT);

        BrokerOrderRecord recorded = pendingOrder().recordCancellation(
                submission
        );

        assertThat(recorded.status()).isEqualTo(BrokerOrderStatus.PENDING);
        assertThat(recorded.cancellationSubmission()).isEqualTo(submission);
    }

    @Test
    void rejectsCancellationForTerminalOrder() {
        BrokerOrderRecord filledOrder = order(
                orderReference(),
                10L,
                69_800L,
                BrokerOrderStatus.FILLED,
                null,
                EXPIRES_AT
        );

        assertThatThrownBy(() -> filledOrder.recordCancellation(
                acceptedCancellation(CANCELLATION_SUBMITTED_AT)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Only pending or partially filled orders can be "
                                + "canceled."
                );
    }

    @Test
    void rejectsDuplicateCancellationSubmission() {
        BrokerOrderRecord recorded = pendingOrder().recordCancellation(
                acceptedCancellation(CANCELLATION_SUBMITTED_AT)
        );

        assertThatThrownBy(() -> recorded.recordCancellation(
                rejectedCancellation(CANCELLATION_SUBMITTED_AT.plusSeconds(1))
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Cancellation submission has already been recorded."
                );
    }

    @Test
    void rejectsCancellationSubmittedBeforeOrder() {
        assertThatThrownBy(() -> pendingOrder().recordCancellation(
                acceptedCancellation(SUBMITTED_AT.minusSeconds(1))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "cancellation submittedAt must not be before "
                                + "submittedAt."
                );
    }

    @Test
    void preservesCancellationSubmissionAfterReconciliation() {
        BrokerOrderCancellationSubmission cancellation = acceptedCancellation(
                CANCELLATION_SUBMITTED_AT
        );
        BrokerOrderRecord order = pendingOrder().recordCancellation(
                cancellation
        );

        BrokerOrderRecord reconciled = order.reconcile(
                BrokerOrderInquiryResult.found(
                        snapshot(
                                orderReference(),
                                10L,
                                0L,
                                null,
                                BrokerOrderStatus.CANCELED
                        ),
                        SUBMITTED_AT.plusSeconds(30)
                )
        );

        assertThat(reconciled.status()).isEqualTo(BrokerOrderStatus.CANCELED);
        assertThat(reconciled.cancellationSubmission())
                .isEqualTo(cancellation);
    }

    @Test
    void rejectsStatusThatDoesNotMatchFilledQuantity() {
        assertThatThrownBy(() -> order(
                orderReference(),
                5L,
                69_900L,
                BrokerOrderStatus.PENDING,
                null,
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PENDING order must not have a filled quantity.");

        assertThatThrownBy(() -> order(
                orderReference(),
                9L,
                69_900L,
                BrokerOrderStatus.FILLED,
                null,
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("FILLED order must have the requested quantity filled.");
    }

    @Test
    void rejectsFilledOrderWithoutAverageFilledPrice() {
        assertThatThrownBy(() -> order(
                orderReference(),
                5L,
                null,
                BrokerOrderStatus.PARTIALLY_FILLED,
                null,
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "averageFilledPriceKrw must be positive when an order is filled."
                );
    }

    @Test
    void reconcilesPendingOrderWithPartialExecution() {
        Instant observedAt = SUBMITTED_AT.plusSeconds(30);

        BrokerOrderRecord reconciled = pendingOrder().reconcile(
                BrokerOrderInquiryResult.found(
                        snapshot(
                                orderReference(),
                                10L,
                                3L,
                                69_900L,
                                BrokerOrderStatus.PARTIALLY_FILLED
                        ),
                        observedAt
                )
        );

        assertThat(reconciled.status())
                .isEqualTo(BrokerOrderStatus.PARTIALLY_FILLED);
        assertThat(reconciled.cumulativeFilledQuantity()).isEqualTo(3L);
        assertThat(reconciled.averageFilledPriceKrw()).isEqualTo(69_900L);
        assertThat(reconciled.lastReconciledAt()).isEqualTo(observedAt);
        assertThat(reconciled.runId()).isEqualTo("run-1");
        assertThat(reconciled.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void recordsNotFoundObservationWithoutChangingExecutionState() {
        Instant observedAt = SUBMITTED_AT.plusSeconds(30);

        BrokerOrderRecord reconciled = pendingOrder().reconcile(
                BrokerOrderInquiryResult.notFound(observedAt)
        );

        assertThat(reconciled.status()).isEqualTo(BrokerOrderStatus.PENDING);
        assertThat(reconciled.cumulativeFilledQuantity()).isZero();
        assertThat(reconciled.averageFilledPriceKrw()).isNull();
        assertThat(reconciled.lastReconciledAt()).isEqualTo(observedAt);
    }

    @Test
    void reconcilesPartiallyFilledOrderAsFilled() {
        BrokerOrderRecord partiallyFilled = order(
                orderReference(),
                3L,
                69_900L,
                BrokerOrderStatus.PARTIALLY_FILLED,
                null,
                EXPIRES_AT
        );

        BrokerOrderRecord reconciled = partiallyFilled.reconcile(
                BrokerOrderInquiryResult.found(
                        snapshot(
                                orderReference(),
                                10L,
                                10L,
                                69_800L,
                                BrokerOrderStatus.FILLED
                        ),
                        SUBMITTED_AT.plusSeconds(30)
                )
        );

        assertThat(reconciled.status()).isEqualTo(BrokerOrderStatus.FILLED);
        assertThat(reconciled.cumulativeFilledQuantity()).isEqualTo(10L);
        assertThat(reconciled.averageFilledPriceKrw()).isEqualTo(69_800L);
    }

    @Test
    void reconcilesPendingOrderAsCanceled() {
        BrokerOrderRecord reconciled = pendingOrder().reconcile(
                BrokerOrderInquiryResult.found(
                        snapshot(
                                orderReference(),
                                10L,
                                0L,
                                null,
                                BrokerOrderStatus.CANCELED
                        ),
                        SUBMITTED_AT.plusSeconds(30)
                )
        );

        assertThat(reconciled.status()).isEqualTo(BrokerOrderStatus.CANCELED);
        assertThat(reconciled.cumulativeFilledQuantity()).isZero();
        assertThat(reconciled.averageFilledPriceKrw()).isNull();
    }

    @Test
    void rejectsReconciliationThatDecreasesFilledQuantity() {
        BrokerOrderRecord partiallyFilled = order(
                orderReference(),
                3L,
                69_900L,
                BrokerOrderStatus.PARTIALLY_FILLED,
                null,
                EXPIRES_AT
        );

        assertThatThrownBy(() -> partiallyFilled.reconcile(
                BrokerOrderInquiryResult.found(
                        snapshot(
                                orderReference(),
                                10L,
                                2L,
                                69_800L,
                                BrokerOrderStatus.PARTIALLY_FILLED
                        ),
                        SUBMITTED_AT.plusSeconds(30)
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "snapshot cumulativeFilledQuantity must not decrease."
                );
    }

    @Test
    void rejectsReconciliationForDifferentOrder() {
        BrokerOrderReference differentReference =
                new BrokerOrderReference("0000999999", "06010");

        assertThatThrownBy(() -> pendingOrder().reconcile(
                BrokerOrderInquiryResult.found(
                        snapshot(
                                differentReference,
                                10L,
                                0L,
                                null,
                                BrokerOrderStatus.PENDING
                        ),
                        SUBMITTED_AT.plusSeconds(30)
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "snapshot reference must match order reference."
                );
    }

    @Test
    void rejectsReconciliationWithDifferentRequestedQuantity() {
        assertThatThrownBy(() -> pendingOrder().reconcile(
                BrokerOrderInquiryResult.found(
                        snapshot(
                                orderReference(),
                                9L,
                                0L,
                                null,
                                BrokerOrderStatus.PENDING
                        ),
                        SUBMITTED_AT.plusSeconds(30)
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "snapshot requestedQuantity must match order "
                                + "requestedQuantity."
                );
    }

    @Test
    void rejectsReconciliationObservedBeforeSubmission() {
        assertThatThrownBy(() -> pendingOrder().reconcile(
                BrokerOrderInquiryResult.notFound(
                        SUBMITTED_AT.minusSeconds(1)
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "reconciledAt must not be before submittedAt."
                );
    }

    @Test
    void rejectsReconciliationOlderThanPreviousReconciliation() {
        Instant lastReconciledAt = SUBMITTED_AT.plusSeconds(30);
        BrokerOrderRecord previouslyReconciled = new BrokerOrderRecord(
                null,
                orderReference(),
                "run-1",
                new InvestmentStrategyIdentity(
                        "DAY_TRADING_V1",
                        1,
                        InvestmentHorizon.DAY_TRADING
                ),
                BrokerOrderSide.BUY,
                "005930",
                10L,
                70_000L,
                0L,
                null,
                BrokerOrderStatus.PENDING,
                null,
                SUBMITTED_AT,
                EXPIRES_AT,
                lastReconciledAt
        );

        assertThatThrownBy(() -> previouslyReconciled.reconcile(
                BrokerOrderInquiryResult.notFound(
                        lastReconciledAt.minusSeconds(1)
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "reconciledAt must not be before lastReconciledAt."
                );
    }

    private BrokerOrderRecord pendingOrder() {
        return order(
                orderReference(),
                0L,
                null,
                BrokerOrderStatus.PENDING,
                null,
                EXPIRES_AT
        );
    }

    private BrokerOrderRecord order(
            BrokerOrderReference reference,
            long cumulativeFilledQuantity,
            Long averageFilledPriceKrw,
            BrokerOrderStatus status,
            String reason,
            Instant expiresAt
    ) {
        return new BrokerOrderRecord(
                null,
                reference,
                "run-1",
                new InvestmentStrategyIdentity(
                        "DAY_TRADING_V1",
                        1,
                        InvestmentHorizon.DAY_TRADING
                ),
                BrokerOrderSide.BUY,
                "005930",
                10L,
                70_000L,
                cumulativeFilledQuantity,
                averageFilledPriceKrw,
                status,
                reason,
                SUBMITTED_AT,
                expiresAt,
                null
        );
    }

    private BrokerOrderExecutionSnapshot snapshot(
            BrokerOrderReference reference,
            long requestedQuantity,
            long cumulativeFilledQuantity,
            Long averageFilledPriceKrw,
            BrokerOrderStatus status
    ) {
        return new BrokerOrderExecutionSnapshot(
                reference,
                requestedQuantity,
                cumulativeFilledQuantity,
                averageFilledPriceKrw,
                status,
                null
        );
    }

    private BrokerOrderReference orderReference() {
        return new BrokerOrderReference("0000123456", "06010");
    }

    private BrokerOrderCancellationSubmission acceptedCancellation(
            Instant submittedAt
    ) {
        return new BrokerOrderCancellationSubmission(
                BrokerOrderCancellationSubmissionStatus.ACCEPTED,
                new BrokerOrderReference("0000123457", "06010"),
                submittedAt,
                null
        );
    }

    private BrokerOrderCancellationSubmission rejectedCancellation(
            Instant submittedAt
    ) {
        return new BrokerOrderCancellationSubmission(
                BrokerOrderCancellationSubmissionStatus.REJECTED,
                null,
                submittedAt,
                "The order cannot be canceled."
        );
    }
}
