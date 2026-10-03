package com.stock.strategy.universe.eligibility.result;

import com.stock.strategy.universe.eligibility.input.StockEligibilityInput;
import com.stock.strategy.universe.eligibility.request.StockEligibilityRequest;

import java.util.Objects;

public record StockEligibilityResult(
        StockEligibilityRequest request,
        StockEligibilityInput input,
        StockEligibilityStatus status,
        StockEligibilityReasonCode reasonCode
) {
    public StockEligibilityResult {
        Objects.requireNonNull(request, "request must not be null.");
        Objects.requireNonNull(input, "input must not be null.");
        Objects.requireNonNull(status, "status must not be null.");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null.");
        if (status != reasonCode.status()) {
            throw new IllegalArgumentException("status must match reasonCode status.");
        }
    }
}
