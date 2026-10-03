package com.stock.strategy.universe.eligibility.input;

import java.time.Instant;
import java.time.LocalDate;

public record StockEligibilityInput(
        String symbol,
        LocalDate asOfDate,
        StockMarket market,
        StockSecurityType securityType,
        StockListingStatus listingStatus,
        String sourceReference,
        Instant informationAvailableAt,
        StockEligibilityEvidenceStatus evidenceStatus
) {
    public StockEligibilityInput {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        // Missing metadata stays null; the policy must not infer a past listing or type.
    }
}
