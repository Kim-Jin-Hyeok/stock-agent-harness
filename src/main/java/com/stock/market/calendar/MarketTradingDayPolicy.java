package com.stock.market.calendar;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class MarketTradingDayPolicy {
    private final MarketCalendarProperties marketCalendarProperties;

    public boolean isTradingDay(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null.");

        if (date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }

        return !marketCalendarProperties.closedDates().contains(date);
    }
}
