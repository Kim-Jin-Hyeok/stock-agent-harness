package com.stock.strategy.universe.eligibility.restriction.kis.result;

public enum KisStockTradingFlagStatus {
    Y_OBSERVED,
    N_OBSERVED,
    VALUE_UNVERIFIED,
    FIELD_NOT_PROVIDED;

    public static KisStockTradingFlagStatus fromRawValue(String rawValue) {
        // Literal observations do not grant current or historical trading permission.
        return switch (rawValue) {
            case null -> FIELD_NOT_PROVIDED;
            case "Y" -> Y_OBSERVED;
            case "N" -> N_OBSERVED;
            default -> VALUE_UNVERIFIED;
        };
    }
}
