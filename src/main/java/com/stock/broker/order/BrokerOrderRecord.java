package com.stock.broker.order;

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
        Long averageFilledPriceKrw,
        BrokerOrderStatus status,
        String reason,
        Instant submittedAt,
        Instant expiresAt,
        Instant lastReconciledAt
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
}
