package com.stock.market.price.history.provider.kis;

import com.stock.market.price.history.TradingVenueScope;

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

    public TradingVenueScope toTradingVenueScope() {
        return switch (this) {
            case KRX -> TradingVenueScope.KRX;
            case NXT -> TradingVenueScope.NXT;
            case INTEGRATED -> TradingVenueScope.INTEGRATED;
        };
    }
}
