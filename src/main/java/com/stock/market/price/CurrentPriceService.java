package com.stock.market.price;

import com.stock.market.price.provider.CurrentPriceProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentPriceService {
    private final CurrentPriceProvider currentPriceProvider;

    public CurrentPriceSnapshot getCurrentPrice(String symbol) {
        return currentPriceProvider.getCurrentPrice(symbol);
    }
}
