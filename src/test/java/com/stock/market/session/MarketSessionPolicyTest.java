package com.stock.market.session;

import com.stock.market.calendar.MarketCalendarProperties;
import com.stock.market.calendar.MarketTradingDayPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MarketSessionPolicyTest {

    @Test
    void returnsTrueAtRegularMarketOpen() {
        MarketSessionPolicy policy = policyWithClosedDates(Set.of());

        assertThat(policy.isOpen(LocalDateTime.of(2026, 1, 2, 9, 0)))
                .isTrue();
    }

    @Test
    void returnsFalseBeforeRegularMarketOpen() {
        MarketSessionPolicy policy = policyWithClosedDates(Set.of());

        assertThat(policy.isOpen(LocalDateTime.of(2026, 1, 2, 8, 59, 59)))
                .isFalse();
    }

    @Test
    void returnsFalseAtRegularMarketClose() {
        MarketSessionPolicy policy = policyWithClosedDates(Set.of());

        assertThat(policy.isOpen(LocalDateTime.of(2026, 1, 2, 15, 30)))
                .isFalse();
    }

    @Test
    void returnsFalseOnConfiguredClosedDate() {
        LocalDate closedDate = LocalDate.of(2026, 1, 2);
        MarketSessionPolicy policy = policyWithClosedDates(Set.of(closedDate));

        assertThat(policy.isOpen(closedDate.atTime(10, 0))).isFalse();
    }

    @Test
    void returnsFalseOnWeekend() {
        MarketSessionPolicy policy = policyWithClosedDates(Set.of());

        assertThat(policy.isOpen(LocalDateTime.of(2026, 1, 3, 10, 0)))
                .isFalse();
    }

    private MarketSessionPolicy policyWithClosedDates(Set<LocalDate> closedDates) {
        MarketTradingDayPolicy tradingDayPolicy = new MarketTradingDayPolicy(
                new MarketCalendarProperties(closedDates)
        );
        return new MarketSessionPolicy(tradingDayPolicy);
    }
}
