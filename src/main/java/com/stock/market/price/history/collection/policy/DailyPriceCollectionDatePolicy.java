package com.stock.market.price.history.collection.policy;

import com.stock.market.calendar.MarketTradingDayPolicy;
import com.stock.market.price.history.collection.config.DailyPriceHistoryCollectionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class DailyPriceCollectionDatePolicy {
    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final MarketTradingDayPolicy marketTradingDayPolicy;
    private final DailyPriceHistoryCollectionProperties properties;
    private final Clock clock;

    public LocalDate getLatestCompletedTradingDate() {
        LocalDateTime evaluatedAt = LocalDateTime.ofInstant(
                clock.instant(),
                KOREA_ZONE_ID
        );
        LocalDate evaluatedDate = evaluatedAt.toLocalDate();

        if (marketTradingDayPolicy.isTradingDay(evaluatedDate)
                && !evaluatedAt.toLocalTime().isBefore(
                        properties.dailyBarAvailableAt()
                )) {
            return evaluatedDate;
        }

        LocalDate candidate = evaluatedDate.minusDays(1);
        while (!marketTradingDayPolicy.isTradingDay(candidate)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }
}
