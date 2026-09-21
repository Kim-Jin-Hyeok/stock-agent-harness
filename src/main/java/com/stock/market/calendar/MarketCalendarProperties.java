package com.stock.market.calendar;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

@ConfigurationProperties(prefix = "market.calendar")
public record MarketCalendarProperties(
        Set<LocalDate> closedDates
) {
    public MarketCalendarProperties {
        closedDates = Set.copyOf(Objects.requireNonNull(
                closedDates,
                "Market closedDates must not be null."
        ));
    }
}
