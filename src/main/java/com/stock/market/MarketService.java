package com.stock.market;

import org.springframework.stereotype.Component;

@Component
public class MarketService {

    public MarketSnapshot getCurrentSnapshot() {
        return new MarketSnapshot(
                "KR",
                false,
                "Market snapshot is using fixed local data"
        );
    }
}
