package com.stock.broker.order.inquiry;

import com.stock.broker.order.BrokerOrderReference;
import com.stock.broker.order.BrokerOrderStatus;

import java.util.Objects;

public record BrokerOrderExecutionSnapshot(
        BrokerOrderReference reference,
        long requestedQuantity,
        long cumulativeFilledQuantity,
        long cumulativeFilledAmountKrw,
        Long averageFilledPriceKrw,
        BrokerOrderStatus status,
        String reason
) {
    public BrokerOrderExecutionSnapshot {
        Objects.requireNonNull(reference, "reference must not be null.");
        Objects.requireNonNull(status, "status must not be null.");

        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException(
                    "requestedQuantity must be positive."
            );
        }
        if (cumulativeFilledQuantity < 0
                || cumulativeFilledQuantity > requestedQuantity) {
            throw new IllegalArgumentException(
                    "cumulativeFilledQuantity must be between 0 "
                            + "and requestedQuantity."
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
        validateStatus(
                status,
                cumulativeFilledQuantity,
                requestedQuantity,
                reason
        );
    }

    public BrokerOrderExecutionSnapshot(
            BrokerOrderReference reference,
            long requestedQuantity,
            long cumulativeFilledQuantity,
            Long averageFilledPriceKrw,
            BrokerOrderStatus status,
            String reason
    ) {
        this(
                reference,
                requestedQuantity,
                cumulativeFilledQuantity,
                inferredFilledAmountKrw(
                        cumulativeFilledQuantity,
                        averageFilledPriceKrw
                ),
                averageFilledPriceKrw,
                status,
                reason
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
                && (averageFilledPriceKrw == null
                || averageFilledPriceKrw <= 0)) {
            throw new IllegalArgumentException(
                    "averageFilledPriceKrw must be positive when an order is filled."
            );
        }
    }

    private static void validateStatus(
            BrokerOrderStatus status,
            long cumulativeFilledQuantity,
            long requestedQuantity,
            String reason
    ) {
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
}
