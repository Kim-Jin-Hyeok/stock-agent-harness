package com.stock.market.price.provider;

import com.stock.market.price.CurrentPriceSnapshot;

public interface CurrentPriceProvider {
    CurrentPriceSnapshot getCurrentPrice(String symbol);
}
