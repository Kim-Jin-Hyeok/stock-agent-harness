package com.stock.broker.order;

public record BrokerOrderReference(
        String orderId,
        String organizationNumber
) {
    public BrokerOrderReference {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank.");
        }
        if (organizationNumber != null && organizationNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "organizationNumber must not be blank when present."
            );
        }
    }
}
