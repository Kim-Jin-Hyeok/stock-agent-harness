package com.stock.harness.scheduler.window;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyRunWindowTest {

    @Test
    void copiesAllowedDays() {
        Set<DayOfWeek> allowedDays = EnumSet.of(DayOfWeek.MONDAY);
        StrategyRunWindow window = new StrategyRunWindow(
                allowedDays,
                LocalTime.of(9, 0),
                LocalTime.of(15, 30)
        );

        allowedDays.add(DayOfWeek.TUESDAY);

        assertThat(window.allowedDays()).containsExactly(DayOfWeek.MONDAY);
        assertThatThrownBy(() -> window.allowedDays().add(DayOfWeek.WEDNESDAY))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsEmptyAllowedDays() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StrategyRunWindow(
                        Set.of(),
                        LocalTime.of(9, 0),
                        LocalTime.of(15, 30)
                ))
                .withMessage("allowedDays must not be empty.");
    }

    @Test
    void rejectsMissingAllowedDays() {
        assertThatNullPointerException()
                .isThrownBy(() -> new StrategyRunWindow(
                        null,
                        LocalTime.of(9, 0),
                        LocalTime.of(15, 30)
                ))
                .withMessage("allowedDays must not be null.");
    }

    @Test
    void rejectsMissingStartTime() {
        assertThatNullPointerException()
                .isThrownBy(() -> new StrategyRunWindow(
                        Set.of(DayOfWeek.MONDAY),
                        null,
                        LocalTime.of(15, 30)
                ))
                .withMessage("startsAt must not be null.");
    }

    @Test
    void rejectsMissingEndTime() {
        assertThatNullPointerException()
                .isThrownBy(() -> new StrategyRunWindow(
                        Set.of(DayOfWeek.MONDAY),
                        LocalTime.of(9, 0),
                        null
                ))
                .withMessage("endsAt must not be null.");
    }

    @Test
    void rejectsWindowThatCrossesMidnight() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StrategyRunWindow(
                        Set.of(DayOfWeek.MONDAY),
                        LocalTime.of(15, 30),
                        LocalTime.of(9, 0)
                ))
                .withMessage("startsAt must not be after endsAt.");
    }
}
