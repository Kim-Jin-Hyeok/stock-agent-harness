package com.stock.broker.order;

import com.stock.broker.order.cancellation.BrokerOrderCancellationSubmission;
import com.stock.broker.order.inquiry.BrokerOrderExecutionSnapshot;
import com.stock.broker.order.inquiry.BrokerOrderInquiryResult;
import com.stock.broker.order.inquiry.BrokerOrderInquiryStatus;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

import java.time.Instant;
import java.util.Objects;

public record BrokerOrderRecord(
        Long id,
        BrokerOrderReference reference,
        String runId,
        InvestmentStrategyIdentity strategyIdentity,
        BrokerOrderSide side,
        String symbol,
        long requestedQuantity,
        long limitPriceKrw,
        long cumulativeFilledQuantity,
        long cumulativeFilledAmountKrw,
        long portfolioAppliedQuantity,
        long portfolioAppliedAmountKrw,
        Long averageFilledPriceKrw,
        BrokerOrderStatus status,
        String reason,
        Instant submittedAt,
        Instant expiresAt,
        Instant lastReconciledAt,
        BrokerOrderCancellationSubmission cancellationSubmission
) {
    public BrokerOrderRecord {
        if (id != null && id < 1) {
            throw new IllegalArgumentException("id must be positive when present.");
        }
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank.");
        }

        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        Objects.requireNonNull(side, "side must not be null.");
        Objects.requireNonNull(status, "status must not be null.");

        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException("requestedQuantity must be positive.");
        }
        if (limitPriceKrw <= 0) {
            throw new IllegalArgumentException("limitPriceKrw must be positive.");
        }
        if (cumulativeFilledQuantity < 0
                || cumulativeFilledQuantity > requestedQuantity) {
            throw new IllegalArgumentException(
                    "cumulativeFilledQuantity must be between 0 and requestedQuantity."
            );
        }
        validateAverageFilledPrice(
                cumulativeFilledQuantity,
                averageFilledPriceKrw
        );
        validateFilledAmount(
                cumulativeFilledQuantity,
                cumulativeFilledAmountKrw
        );
        if (portfolioAppliedQuantity < 0
                || portfolioAppliedQuantity > cumulativeFilledQuantity) {
            throw new IllegalArgumentException(
                    "portfolioAppliedQuantity must be between 0 and "
                            + "cumulativeFilledQuantity."
            );
        }
        validatePortfolioAppliedAmount(
                cumulativeFilledQuantity,
                cumulativeFilledAmountKrw,
                portfolioAppliedQuantity,
                portfolioAppliedAmountKrw
        );

        validateStatus(
                status,
                reference,
                cumulativeFilledQuantity,
                requestedQuantity,
                reason
        );

        Objects.requireNonNull(submittedAt, "submittedAt must not be null.");
        validateExpiration(status, submittedAt, expiresAt);
        if (lastReconciledAt != null && lastReconciledAt.isBefore(submittedAt)) {
            throw new IllegalArgumentException(
                    "lastReconciledAt must not be before submittedAt."
            );
        }
        validateCancellationSubmission(
                status,
                submittedAt,
                cancellationSubmission
        );
    }

    public BrokerOrderRecord(
            Long id,
            BrokerOrderReference reference,
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            BrokerOrderSide side,
            String symbol,
            long requestedQuantity,
            long limitPriceKrw,
            long cumulativeFilledQuantity,
            Long averageFilledPriceKrw,
            BrokerOrderStatus status,
            String reason,
            Instant submittedAt,
            Instant expiresAt,
            Instant lastReconciledAt
    ) {
        this(
                id,
                reference,
                runId,
                strategyIdentity,
                side,
                symbol,
                requestedQuantity,
                limitPriceKrw,
                cumulativeFilledQuantity,
                inferredFilledAmountKrw(
                        cumulativeFilledQuantity,
                        averageFilledPriceKrw
                ),
                0L,
                0L,
                averageFilledPriceKrw,
                status,
                reason,
                submittedAt,
                expiresAt,
                lastReconciledAt,
                null
        );
    }

    public BrokerOrderRecord(
            Long id,
            BrokerOrderReference reference,
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            BrokerOrderSide side,
            String symbol,
            long requestedQuantity,
            long limitPriceKrw,
            long cumulativeFilledQuantity,
            Long averageFilledPriceKrw,
            BrokerOrderStatus status,
            String reason,
            Instant submittedAt,
            Instant expiresAt,
            Instant lastReconciledAt,
            BrokerOrderCancellationSubmission cancellationSubmission
    ) {
        this(
                id,
                reference,
                runId,
                strategyIdentity,
                side,
                symbol,
                requestedQuantity,
                limitPriceKrw,
                cumulativeFilledQuantity,
                inferredFilledAmountKrw(
                        cumulativeFilledQuantity,
                        averageFilledPriceKrw
                ),
                0L,
                0L,
                averageFilledPriceKrw,
                status,
                reason,
                submittedAt,
                expiresAt,
                lastReconciledAt,
                cancellationSubmission
        );
    }

    public BrokerOrderRecord(
            Long id,
            BrokerOrderReference reference,
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            BrokerOrderSide side,
            String symbol,
            long requestedQuantity,
            long limitPriceKrw,
            long cumulativeFilledQuantity,
            long portfolioAppliedQuantity,
            Long averageFilledPriceKrw,
            BrokerOrderStatus status,
            String reason,
            Instant submittedAt,
            Instant expiresAt,
            Instant lastReconciledAt,
            BrokerOrderCancellationSubmission cancellationSubmission
    ) {
        this(
                id,
                reference,
                runId,
                strategyIdentity,
                side,
                symbol,
                requestedQuantity,
                limitPriceKrw,
                cumulativeFilledQuantity,
                inferredFilledAmountKrw(
                        cumulativeFilledQuantity,
                        averageFilledPriceKrw
                ),
                portfolioAppliedQuantity,
                inferredAppliedAmountKrw(
                        cumulativeFilledQuantity,
                        portfolioAppliedQuantity,
                        averageFilledPriceKrw
                ),
                averageFilledPriceKrw,
                status,
                reason,
                submittedAt,
                expiresAt,
                lastReconciledAt,
                cancellationSubmission
        );
    }

    public long unappliedFilledQuantity() {
        return cumulativeFilledQuantity - portfolioAppliedQuantity;
    }

    public long unappliedFilledAmountKrw() {
        return cumulativeFilledAmountKrw - portfolioAppliedAmountKrw;
    }

    public BrokerOrderRecord markCurrentFillAppliedToPortfolio() {
        if (unappliedFilledQuantity() == 0) {
            throw new IllegalStateException(
                    "No unapplied filled quantity is available."
            );
        }

        return new BrokerOrderRecord(
                id,
                reference,
                runId,
                strategyIdentity,
                side,
                symbol,
                requestedQuantity,
                limitPriceKrw,
                cumulativeFilledQuantity,
                cumulativeFilledAmountKrw,
                cumulativeFilledQuantity,
                cumulativeFilledAmountKrw,
                averageFilledPriceKrw,
                status,
                reason,
                submittedAt,
                expiresAt,
                lastReconciledAt,
                cancellationSubmission
        );
    }

    public BrokerOrderRecord reconcile(BrokerOrderInquiryResult result) {
        Objects.requireNonNull(result, "result must not be null.");
        if (status != BrokerOrderStatus.PENDING
                && status != BrokerOrderStatus.PARTIALLY_FILLED) {
            throw new IllegalStateException(
                    "Only pending or partially filled orders can be reconciled."
            );
        }

        validateReconciledAt(result.observedAt());
        if (result.status() == BrokerOrderInquiryStatus.NOT_FOUND) {
            return withExecutionState(
                    cumulativeFilledQuantity,
                    cumulativeFilledAmountKrw,
                    averageFilledPriceKrw,
                    status,
                    reason,
                    result.observedAt()
            );
        }

        BrokerOrderExecutionSnapshot snapshot = result.snapshot();
        validateSnapshot(snapshot);
        return withExecutionState(
                snapshot.cumulativeFilledQuantity(),
                snapshot.cumulativeFilledAmountKrw(),
                snapshot.averageFilledPriceKrw(),
                snapshot.status(),
                snapshot.reason(),
                result.observedAt()
        );
    }

    public BrokerOrderRecord recordCancellation(
            BrokerOrderCancellationSubmission submission
    ) {
        Objects.requireNonNull(submission, "submission must not be null.");
        if (status != BrokerOrderStatus.PENDING
                && status != BrokerOrderStatus.PARTIALLY_FILLED) {
            throw new IllegalStateException(
                    "Only pending or partially filled orders can be canceled."
            );
        }
        if (cancellationSubmission != null) {
            throw new IllegalStateException(
                    "Cancellation submission has already been recorded."
            );
        }
        if (lastReconciledAt != null
                && submission.submittedAt().isBefore(lastReconciledAt)) {
            throw new IllegalArgumentException(
                    "cancellation submittedAt must not be before "
                            + "lastReconciledAt."
            );
        }

        return new BrokerOrderRecord(
                id,
                reference,
                runId,
                strategyIdentity,
                side,
                symbol,
                requestedQuantity,
                limitPriceKrw,
                cumulativeFilledQuantity,
                cumulativeFilledAmountKrw,
                portfolioAppliedQuantity,
                portfolioAppliedAmountKrw,
                averageFilledPriceKrw,
                status,
                reason,
                submittedAt,
                expiresAt,
                lastReconciledAt,
                submission
        );
    }

    private void validateReconciledAt(Instant reconciledAt) {
        if (reconciledAt.isBefore(submittedAt)) {
            throw new IllegalArgumentException(
                    "reconciledAt must not be before submittedAt."
            );
        }
        if (lastReconciledAt != null
                && reconciledAt.isBefore(lastReconciledAt)) {
            throw new IllegalArgumentException(
                    "reconciledAt must not be before lastReconciledAt."
            );
        }
    }

    private void validateSnapshot(BrokerOrderExecutionSnapshot snapshot) {
        if (!reference.equals(snapshot.reference())) {
            throw new IllegalArgumentException(
                    "snapshot reference must match order reference."
            );
        }
        if (requestedQuantity != snapshot.requestedQuantity()) {
            throw new IllegalArgumentException(
                    "snapshot requestedQuantity must match order requestedQuantity."
            );
        }
        if (snapshot.cumulativeFilledQuantity()
                < cumulativeFilledQuantity) {
            throw new IllegalArgumentException(
                    "snapshot cumulativeFilledQuantity must not decrease."
            );
        }
        if (snapshot.cumulativeFilledAmountKrw()
                < cumulativeFilledAmountKrw) {
            throw new IllegalArgumentException(
                    "snapshot cumulativeFilledAmountKrw must not decrease."
            );
        }
    }

    private BrokerOrderRecord withExecutionState(
            long reconciledQuantity,
            long reconciledAmountKrw,
            Long reconciledAveragePriceKrw,
            BrokerOrderStatus reconciledStatus,
            String reconciledReason,
            Instant reconciledAt
    ) {
        return new BrokerOrderRecord(
                id,
                reference,
                runId,
                strategyIdentity,
                side,
                symbol,
                requestedQuantity,
                limitPriceKrw,
                reconciledQuantity,
                reconciledAmountKrw,
                portfolioAppliedQuantity,
                portfolioAppliedAmountKrw,
                reconciledAveragePriceKrw,
                reconciledStatus,
                reconciledReason,
                submittedAt,
                expiresAt,
                reconciledAt,
                cancellationSubmission
        );
    }

    private static void validateFilledAmount(
            long cumulativeFilledQuantity,
            long cumulativeFilledAmountKrw
    ) {
        if (cumulativeFilledQuantity == 0
                && cumulativeFilledAmountKrw != 0) {
            throw new IllegalArgumentException(
                    "cumulativeFilledAmountKrw must be zero when nothing is filled."
            );
        }
        if (cumulativeFilledQuantity > 0
                && cumulativeFilledAmountKrw <= 0) {
            throw new IllegalArgumentException(
                    "cumulativeFilledAmountKrw must be positive when an order is filled."
            );
        }
    }

    private static void validatePortfolioAppliedAmount(
            long cumulativeFilledQuantity,
            long cumulativeFilledAmountKrw,
            long portfolioAppliedQuantity,
            long portfolioAppliedAmountKrw
    ) {
        if (portfolioAppliedAmountKrw < 0
                || portfolioAppliedAmountKrw > cumulativeFilledAmountKrw) {
            throw new IllegalArgumentException(
                    "portfolioAppliedAmountKrw must be between 0 and "
                            + "cumulativeFilledAmountKrw."
            );
        }
        if ((portfolioAppliedQuantity == 0)
                != (portfolioAppliedAmountKrw == 0)) {
            throw new IllegalArgumentException(
                    "portfolio applied quantity and amount must both be zero "
                            + "or both be positive."
            );
        }
        if ((portfolioAppliedQuantity == cumulativeFilledQuantity)
                != (portfolioAppliedAmountKrw
                == cumulativeFilledAmountKrw)) {
            throw new IllegalArgumentException(
                    "fully applied quantity and amount must match."
            );
        }
    }

    private static long inferredFilledAmountKrw(
            long cumulativeFilledQuantity,
            Long averageFilledPriceKrw
    ) {
        if (cumulativeFilledQuantity == 0 || averageFilledPriceKrw == null) {
            return 0L;
        }
        return Math.multiplyExact(
                cumulativeFilledQuantity,
                averageFilledPriceKrw
        );
    }

    private static long inferredAppliedAmountKrw(
            long cumulativeFilledQuantity,
            long portfolioAppliedQuantity,
            Long averageFilledPriceKrw
    ) {
        if (portfolioAppliedQuantity == 0 || averageFilledPriceKrw == null) {
            return 0L;
        }
        if (portfolioAppliedQuantity == cumulativeFilledQuantity) {
            return inferredFilledAmountKrw(
                    cumulativeFilledQuantity,
                    averageFilledPriceKrw
            );
        }
        return Math.multiplyExact(
                portfolioAppliedQuantity,
                averageFilledPriceKrw
        );
    }

    private static void validateAverageFilledPrice(
            long cumulativeFilledQuantity,
            Long averageFilledPriceKrw
    ) {
        if (cumulativeFilledQuantity == 0 && averageFilledPriceKrw != null) {
            throw new IllegalArgumentException(
                    "averageFilledPriceKrw must be null when nothing is filled."
            );
        }
        if (cumulativeFilledQuantity > 0
                && (averageFilledPriceKrw == null || averageFilledPriceKrw <= 0)) {
            throw new IllegalArgumentException(
                    "averageFilledPriceKrw must be positive when an order is filled."
            );
        }
    }

    private static void validateStatus(
            BrokerOrderStatus status,
            BrokerOrderReference reference,
            long cumulativeFilledQuantity,
            long requestedQuantity,
            String reason
    ) {
        if (status != BrokerOrderStatus.REJECTED && reference == null) {
            throw new IllegalArgumentException(
                    "reference must be present for a submitted order."
            );
        }

        switch (status) {
            case PENDING -> {
                if (cumulativeFilledQuantity != 0) {
                    throw new IllegalArgumentException(
                            "PENDING order must not have a filled quantity."
                    );
                }
            }
            case PARTIALLY_FILLED -> {
                if (cumulativeFilledQuantity <= 0
                        || cumulativeFilledQuantity >= requestedQuantity) {
                    throw new IllegalArgumentException(
                            "PARTIALLY_FILLED order must have a partial filled quantity."
                    );
                }
            }
            case FILLED -> {
                if (cumulativeFilledQuantity != requestedQuantity) {
                    throw new IllegalArgumentException(
                            "FILLED order must have the requested quantity filled."
                    );
                }
            }
            case CANCELED -> {
                if (cumulativeFilledQuantity == requestedQuantity) {
                    throw new IllegalArgumentException(
                            "CANCELED order must have an unfilled quantity."
                    );
                }
            }
            case REJECTED -> {
                if (cumulativeFilledQuantity != 0) {
                    throw new IllegalArgumentException(
                            "REJECTED order must not have a filled quantity."
                    );
                }
                if (reason == null || reason.isBlank()) {
                    throw new IllegalArgumentException(
                            "reason must not be blank for a rejected order."
                    );
                }
            }
        }
    }

    private static void validateExpiration(
            BrokerOrderStatus status,
            Instant submittedAt,
            Instant expiresAt
    ) {
        if (status != BrokerOrderStatus.REJECTED && expiresAt == null) {
            throw new NullPointerException(
                    "expiresAt must not be null for a submitted order."
            );
        }
        if (expiresAt != null && !expiresAt.isAfter(submittedAt)) {
            throw new IllegalArgumentException(
                    "expiresAt must be after submittedAt."
            );
        }
    }

    private static void validateCancellationSubmission(
            BrokerOrderStatus status,
            Instant submittedAt,
            BrokerOrderCancellationSubmission cancellationSubmission
    ) {
        if (cancellationSubmission == null) {
            return;
        }
        if (status == BrokerOrderStatus.REJECTED) {
            throw new IllegalArgumentException(
                    "REJECTED order must not have a cancellation submission."
            );
        }
        if (cancellationSubmission.submittedAt().isBefore(submittedAt)) {
            throw new IllegalArgumentException(
                    "cancellation submittedAt must not be before submittedAt."
            );
        }
    }
}
