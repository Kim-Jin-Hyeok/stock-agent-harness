package com.stock.strategy.universe.eligibility.input;

public enum StockSecurityType {
    COMMON_STOCK,
    PREFERRED_STOCK,
    ETF,
    ETN,
    OTHER;

    public boolean isIndividualStock() {
        return this == COMMON_STOCK || this == PREFERRED_STOCK;
    }
}
