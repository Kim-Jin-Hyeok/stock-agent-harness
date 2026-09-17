package com.stock.market.price.provider;

import com.stock.market.price.CurrentPriceSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@RequiredArgsConstructor
public class FixedCurrentPriceProvider implements CurrentPriceProvider {
    private static final long FIXED_CURRENT_PRICE_KRW = 100_000L;
    private final Clock clock;

    @Override
    public CurrentPriceSnapshot getCurrentPrice(String symbol) {
        return new CurrentPriceSnapshot(
                symbol,
                FIXED_CURRENT_PRICE_KRW,
                clock.instant()
        );
    }
}
