package com.stock.market.index.history.provider;

import com.stock.market.index.history.MarketIndexDailyHistory;
import com.stock.market.index.history.MarketIndexDailyHistoryRequest;

public interface MarketIndexDailyHistoryProvider {
    MarketIndexDailyHistory getDailyHistory(
            MarketIndexDailyHistoryRequest request
    );
}
