package com.stock.broker.order.cancellation;

import com.stock.broker.order.BrokerOrderReference;

import java.util.Objects;

public record BrokerOrderCancellationRequest(
        BrokerOrderReference reference
) {
    public BrokerOrderCancellationRequest {
        Objects.requireNonNull(reference, "reference must not be null.");
    }
}
