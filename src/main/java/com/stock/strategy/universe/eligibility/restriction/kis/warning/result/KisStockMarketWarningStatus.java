package com.stock.strategy.universe.eligibility.restriction.kis.warning.result;

import java.util.Objects;

public enum KisStockMarketWarningStatus {
    NO_WARNING_OBSERVED,
    INVESTMENT_CAUTION_OBSERVED,
    INVESTMENT_WARNING_OBSERVED,
    INVESTMENT_RISK_OBSERVED,
    VALUE_UNVERIFIED;

    public static KisStockMarketWarningStatus fromRawValue(String rawValue) {
        Objects.requireNonNull(rawValue, "raw market warning code must not be null.");
        // These literal code observations do not certify effective designations or trading permission.
        return switch (rawValue) {
            case "00" -> NO_WARNING_OBSERVED;
            case "01" -> INVESTMENT_CAUTION_OBSERVED;
            case "02" -> INVESTMENT_WARNING_OBSERVED;
            case "03" -> INVESTMENT_RISK_OBSERVED;
            default -> VALUE_UNVERIFIED;
        };
    }
}
