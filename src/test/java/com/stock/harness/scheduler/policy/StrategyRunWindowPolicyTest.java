package com.stock.harness.scheduler.policy;

import com.stock.harness.scheduler.window.StrategyRunWindow;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyRunWindowPolicyTest {
    private final StrategyRunWindowPolicy policy = new StrategyRunWindowPolicy();
    private final StrategyRunWindow window = new StrategyRunWindow(
            Set.of(DayOfWeek.MONDAY),
            LocalTime.of(9, 0),
            LocalTime.of(15, 30)
    );

    @Test
    void returnsTrueWithinAllowedWindow() {
        assertThat(policy.isWithinWindow(
                window,
                LocalDateTime.of(2026, 1, 5, 12, 0)
        )).isTrue();
    }

    @Test
    void returnsFalseOnDisallowedDay() {
        assertThat(policy.isWithinWindow(
                window,
                LocalDateTime.of(2026, 1, 6, 12, 0)
        )).isFalse();
    }

    @Test
    void returnsFalseBeforeWindowStarts() {
        assertThat(policy.isWithinWindow(
                window,
                LocalDateTime.of(2026, 1, 5, 8, 59, 59)
        )).isFalse();
    }

    @Test
    void returnsTrueWhenWindowStarts() {
        assertThat(policy.isWithinWindow(
                window,
                LocalDateTime.of(2026, 1, 5, 9, 0)
        )).isTrue();
    }

    @Test
    void returnsTrueWhenWindowEnds() {
        assertThat(policy.isWithinWindow(
                window,
                LocalDateTime.of(2026, 1, 5, 15, 30)
        )).isTrue();
    }

    @Test
    void returnsFalseAfterWindowEnds() {
        assertThat(policy.isWithinWindow(
                window,
                LocalDateTime.of(2026, 1, 5, 15, 30, 1)
        )).isFalse();
    }
}
