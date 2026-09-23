package com.stock.broker.order.inquiry;

import com.stock.broker.order.BrokerOrderReference;

import java.time.Instant;
import java.util.Objects;

public record BrokerOrderInquiryRequest(
        BrokerOrderReference reference,
        String symbol,
        Instant submittedAt
) {
    public BrokerOrderInquiryRequest {
        Objects.requireNonNull(reference, "reference must not be null.");
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        Objects.requireNonNull(submittedAt, "submittedAt must not be null.");
    }
}
