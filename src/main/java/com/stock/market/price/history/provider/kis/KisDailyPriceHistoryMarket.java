package com.stock.market.price.history.provider.kis;

public enum KisDailyPriceHistoryMarket {
    KRX("J"),
    NXT("NX"),
    INTEGRATED("UN");

    private final String code;

    KisDailyPriceHistoryMarket(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
