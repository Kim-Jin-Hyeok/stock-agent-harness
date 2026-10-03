package com.stock.strategy.universe.eligibility.request;

import com.stock.strategy.universe.eligibility.input.StockMarket;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

public record StockEligibilityRequest(
        LocalDate selectionAsOfDate,
        Instant selectionCutoffAt,
        Set<StockMarket> eligibleMarkets,
        Set<StockSecurityType> eligibleSecurityTypes
) {
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Seoul");

    public StockEligibilityRequest {
        Objects.requireNonNull(selectionAsOfDate, "selectionAsOfDate must not be null.");
        Objects.requireNonNull(selectionCutoffAt, "selectionCutoffAt must not be null.");
        if (selectionCutoffAt.atZone(MARKET_ZONE).toLocalDate().isBefore(selectionAsOfDate)) {
            throw new IllegalArgumentException(
                    "selectionCutoffAt must not be before selectionAsOfDate in Asia/Seoul."
            );
        }
        Objects.requireNonNull(eligibleMarkets, "eligibleMarkets must not be null.");
        eligibleMarkets = Set.copyOf(eligibleMarkets);
        if (eligibleMarkets.isEmpty()) {
            throw new IllegalArgumentException("eligibleMarkets must not be empty.");
        }
        if (eligibleMarkets.contains(StockMarket.OTHER)) {
            throw new IllegalArgumentException("eligibleMarkets must contain only KOSPI or KOSDAQ.");
        }
        Objects.requireNonNull(eligibleSecurityTypes, "eligibleSecurityTypes must not be null.");
        eligibleSecurityTypes = Set.copyOf(eligibleSecurityTypes);
        if (eligibleSecurityTypes.isEmpty()) {
            throw new IllegalArgumentException("eligibleSecurityTypes must not be empty.");
        }
        if (eligibleSecurityTypes.stream().anyMatch(type -> !type.isIndividualStock())) {
            throw new IllegalArgumentException(
                    "eligibleSecurityTypes must contain only supported individual stock types."
            );
        }
    }
}
