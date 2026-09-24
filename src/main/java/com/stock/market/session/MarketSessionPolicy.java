package com.stock.market.session;

import com.stock.market.calendar.MarketTradingDayPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class MarketSessionPolicy {
    private static final LocalTime REGULAR_MARKET_OPENS_AT = LocalTime.of(9, 0);
    private static final LocalTime REGULAR_MARKET_CLOSES_AT = LocalTime.of(15, 30);

    private final MarketTradingDayPolicy marketTradingDayPolicy;

    public boolean isOpen(LocalDateTime evaluatedAt) {
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null.");

        if (!marketTradingDayPolicy.isTradingDay(evaluatedAt.toLocalDate())) {
            return false;
        }

        LocalTime evaluatedTime = evaluatedAt.toLocalTime();
        return !evaluatedTime.isBefore(REGULAR_MARKET_OPENS_AT)
                && evaluatedTime.isBefore(REGULAR_MARKET_CLOSES_AT);
    }
}
