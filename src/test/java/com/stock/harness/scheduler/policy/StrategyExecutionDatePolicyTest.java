package com.stock.harness.scheduler.policy;

import com.stock.market.calendar.MarketCalendarProperties;
import com.stock.market.calendar.MarketTradingDayPolicy;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyExecutionDatePolicyTest {
    private static final InvestmentStrategyIdentity DAY_TRADING =
            new InvestmentStrategyIdentity("DAY_TRADING_V1", 1, InvestmentHorizon.DAY_TRADING);
    private static final InvestmentStrategyIdentity SWING =
            new InvestmentStrategyIdentity("SWING_V1", 1, InvestmentHorizon.SWING);
    private static final InvestmentStrategyIdentity LONG_TERM =
            new InvestmentStrategyIdentity("LONG_TERM_V1", 1, InvestmentHorizon.LONG_TERM);

    @Test
    void allowsDayTradingAndSwingOnEveryTradingDay() {
        StrategyExecutionDatePolicy policy = policyWithClosedDates(Set.of());
        LocalDate monday = LocalDate.of(2026, 1, 5);

        assertThat(policy.isExecutionDate(DAY_TRADING, monday)).isTrue();
        assertThat(policy.isExecutionDate(SWING, monday)).isTrue();
    }

    @Test
    void rejectsEveryStrategyOnClosedDate() {
        LocalDate closedDate = LocalDate.of(2026, 1, 5);
        StrategyExecutionDatePolicy policy = policyWithClosedDates(Set.of(closedDate));

        assertThat(policy.isExecutionDate(DAY_TRADING, closedDate)).isFalse();
        assertThat(policy.isExecutionDate(SWING, closedDate)).isFalse();
        assertThat(policy.isExecutionDate(LONG_TERM, closedDate)).isFalse();
    }

    @Test
    void rejectsLongTermBeforeLastTradingDayOfWeek() {
        StrategyExecutionDatePolicy policy = policyWithClosedDates(Set.of());
        LocalDate thursday = LocalDate.of(2026, 1, 8);

        assertThat(policy.isExecutionDate(LONG_TERM, thursday)).isFalse();
    }

    @Test
    void allowsLongTermOnFridayWhenFridayIsTradingDay() {
        StrategyExecutionDatePolicy policy = policyWithClosedDates(Set.of());
        LocalDate friday = LocalDate.of(2026, 1, 9);

        assertThat(policy.isExecutionDate(LONG_TERM, friday)).isTrue();
    }

    @Test
    void allowsLongTermOnThursdayWhenFridayIsClosed() {
        LocalDate thursday = LocalDate.of(2026, 1, 8);
        LocalDate friday = LocalDate.of(2026, 1, 9);
        StrategyExecutionDatePolicy policy = policyWithClosedDates(Set.of(friday));

        assertThat(policy.isExecutionDate(LONG_TERM, thursday)).isTrue();
    }

    @Test
    void allowsLongTermOnWednesdayBeforeChuseokClosure() {
        LocalDate wednesday = LocalDate.of(2026, 9, 23);
        StrategyExecutionDatePolicy policy = policyWithClosedDates(Set.of(
                LocalDate.of(2026, 9, 24),
                LocalDate.of(2026, 9, 25)
        ));

        assertThat(policy.isExecutionDate(LONG_TERM, wednesday)).isTrue();
    }

    private StrategyExecutionDatePolicy policyWithClosedDates(Set<LocalDate> closedDates) {
        MarketCalendarProperties properties = new MarketCalendarProperties(closedDates);
        MarketTradingDayPolicy tradingDayPolicy = new MarketTradingDayPolicy(properties);
        return new StrategyExecutionDatePolicy(tradingDayPolicy);
    }
}
