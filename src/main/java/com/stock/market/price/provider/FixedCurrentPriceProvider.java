package com.stock.market.price.provider;

import com.stock.market.price.CurrentPriceSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@ConditionalOnProperty(
        prefix = "broker.kis",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
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
