package com.stock.market.price.history.collection.policy;

import com.stock.market.calendar.MarketCalendarProperties;
import com.stock.market.calendar.MarketTradingDayPolicy;
import com.stock.market.price.history.collection.config.DailyPriceHistoryCollectionProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DailyPriceCollectionDatePolicyTest {
    private static final LocalDate SEPTEMBER_23 =
            LocalDate.of(2026, 9, 23);
    private static final LocalDate SEPTEMBER_28 =
            LocalDate.of(2026, 9, 28);

    @Test
    void returnsTodayAtDailyBarAvailableTimeOnTradingDay() {
        DailyPriceCollectionDatePolicy policy = policy(
                "2026-09-28T11:10:00Z"
        );

        assertThat(policy.getLatestCompletedTradingDate())
                .isEqualTo(SEPTEMBER_28);
    }

    @Test
    void returnsPreviousTradingDayBeforeDailyBarAvailableTime() {
        DailyPriceCollectionDatePolicy policy = policy(
                "2026-09-28T11:09:59Z"
        );

        assertThat(policy.getLatestCompletedTradingDate())
                .isEqualTo(SEPTEMBER_23);
    }

    @Test
    void returnsPreviousTradingDayOnWeekend() {
        DailyPriceCollectionDatePolicy policy = policy(
                "2026-09-26T11:10:00Z"
        );

        assertThat(policy.getLatestCompletedTradingDate())
                .isEqualTo(SEPTEMBER_23);
    }

    @Test
    void returnsPreviousTradingDayOnMarketHoliday() {
        DailyPriceCollectionDatePolicy policy = policy(
                "2026-09-25T11:10:00Z"
        );

        assertThat(policy.getLatestCompletedTradingDate())
                .isEqualTo(SEPTEMBER_23);
    }

    private DailyPriceCollectionDatePolicy policy(String instant) {
        MarketCalendarProperties calendarProperties =
                new MarketCalendarProperties(Set.of(
                        LocalDate.of(2026, 9, 24),
                        LocalDate.of(2026, 9, 25)
                ));
        MarketTradingDayPolicy tradingDayPolicy =
                new MarketTradingDayPolicy(calendarProperties);
        DailyPriceHistoryCollectionProperties collectionProperties =
                new DailyPriceHistoryCollectionProperties(
                        LocalTime.of(20, 10)
                );
        Clock clock = Clock.fixed(
                Instant.parse(instant),
                ZoneOffset.UTC
        );

        return new DailyPriceCollectionDatePolicy(
                tradingDayPolicy,
                collectionProperties,
                clock
        );
    }
}
