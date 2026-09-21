package com.stock.harness.scheduler.policy;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyRunCadencePolicyTest {
    private static final LocalDateTime EVALUATED_AT = LocalDateTime.of(2026, 1, 5, 9, 0);

    private final StrategyRunCadencePolicy policy = new StrategyRunCadencePolicy();

    @ParameterizedTest
    @EnumSource(InvestmentHorizon.class)
    void returnsDueWhenStrategyHasNoRunHistory(InvestmentHorizon horizon) {
        boolean result = policy.isDue(
                strategyIdentity(horizon),
                EVALUATED_AT,
                Optional.empty()
        );

        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @EnumSource(InvestmentHorizon.class)
    void returnsNotDueWhenLatestRunStartedInFuture(InvestmentHorizon horizon) {
        boolean result = policy.isDue(
                strategyIdentity(horizon),
                EVALUATED_AT,
                Optional.of(EVALUATED_AT.plusMinutes(1))
        );

        assertThat(result).isFalse();
    }

    @Test
    void dayTradingIsNotDueBeforeFiveMinuteBoundary() {
        boolean result = policy.isDue(
                strategyIdentity(InvestmentHorizon.DAY_TRADING),
                EVALUATED_AT,
                Optional.of(EVALUATED_AT.minusMinutes(5).plusNanos(1))
        );

        assertThat(result).isFalse();
    }

    @Test
    void dayTradingIsDueAtFiveMinuteBoundary() {
        boolean result = policy.isDue(
                strategyIdentity(InvestmentHorizon.DAY_TRADING),
                EVALUATED_AT,
                Optional.of(EVALUATED_AT.minusMinutes(5))
        );

        assertThat(result).isTrue();
    }

    @Test
    void swingIsNotDueOnSameDate() {
        boolean result = policy.isDue(
                strategyIdentity(InvestmentHorizon.SWING),
                EVALUATED_AT,
                Optional.of(EVALUATED_AT.minusHours(1))
        );

        assertThat(result).isFalse();
    }

    @Test
    void swingIsDueOnNextDate() {
        boolean result = policy.isDue(
                strategyIdentity(InvestmentHorizon.SWING),
                EVALUATED_AT,
                Optional.of(EVALUATED_AT.minusDays(1).plusHours(1))
        );

        assertThat(result).isTrue();
    }

    @Test
    void longTermIsNotDueInSameIsoWeek() {
        LocalDateTime thursday = LocalDateTime.of(2026, 1, 8, 9, 0);
        LocalDateTime monday = LocalDateTime.of(2026, 1, 5, 9, 0);

        boolean result = policy.isDue(
                strategyIdentity(InvestmentHorizon.LONG_TERM),
                thursday,
                Optional.of(monday)
        );

        assertThat(result).isFalse();
    }

    @Test
    void longTermIsDueInNextIsoWeek() {
        LocalDateTime nextMonday = LocalDateTime.of(2026, 1, 12, 9, 0);
        LocalDateTime previousSunday = LocalDateTime.of(2026, 1, 11, 9, 0);

        boolean result = policy.isDue(
                strategyIdentity(InvestmentHorizon.LONG_TERM),
                nextMonday,
                Optional.of(previousSunday)
        );

        assertThat(result).isTrue();
    }

    @Test
    void longTermIsNotDueWhenYearChangesWithinSameIsoWeek() {
        LocalDateTime newYearThursday = LocalDateTime.of(2026, 1, 1, 9, 0);
        LocalDateTime previousYearWednesday = LocalDateTime.of(2025, 12, 31, 9, 0);

        boolean result = policy.isDue(
                strategyIdentity(InvestmentHorizon.LONG_TERM),
                newYearThursday,
                Optional.of(previousYearWednesday)
        );

        assertThat(result).isFalse();
    }

    private InvestmentStrategyIdentity strategyIdentity(InvestmentHorizon horizon) {
        return new InvestmentStrategyIdentity(
                horizon.name() + "_V1",
                1,
                horizon
        );
    }
}
