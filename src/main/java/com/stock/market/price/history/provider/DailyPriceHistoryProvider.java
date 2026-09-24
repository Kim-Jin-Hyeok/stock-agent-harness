package com.stock.market.price.history.provider;

import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;

public interface DailyPriceHistoryProvider {
    DailyPriceHistory getDailyPriceHistory(DailyPriceHistoryRequest request);
}
