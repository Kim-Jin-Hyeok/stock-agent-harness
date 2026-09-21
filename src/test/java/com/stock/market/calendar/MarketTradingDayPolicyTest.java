package com.stock.market.calendar;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MarketTradingDayPolicyTest {

    @Test
    void returnsTrueOnWeekdayNotConfiguredAsClosed() {
        MarketTradingDayPolicy policy = policyWithClosedDates(Set.of());

        assertThat(policy.isTradingDay(LocalDate.of(2026, 1, 2))).isTrue();
    }

    @Test
    void returnsFalseOnSaturday() {
        MarketTradingDayPolicy policy = policyWithClosedDates(Set.of());

        assertThat(policy.isTradingDay(LocalDate.of(2026, 1, 3))).isFalse();
    }

    @Test
    void returnsFalseOnSunday() {
        MarketTradingDayPolicy policy = policyWithClosedDates(Set.of());

        assertThat(policy.isTradingDay(LocalDate.of(2026, 1, 4))).isFalse();
    }

    @Test
    void returnsFalseOnConfiguredClosedWeekday() {
        LocalDate closedDate = LocalDate.of(2026, 1, 1);
        MarketTradingDayPolicy policy = policyWithClosedDates(Set.of(closedDate));

        assertThat(policy.isTradingDay(closedDate)).isFalse();
    }

    private MarketTradingDayPolicy policyWithClosedDates(Set<LocalDate> closedDates) {
        return new MarketTradingDayPolicy(new MarketCalendarProperties(closedDates));
    }
}
