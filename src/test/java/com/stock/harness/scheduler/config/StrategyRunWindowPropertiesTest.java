package com.stock.harness.scheduler.config;

import com.stock.harness.scheduler.window.StrategyRunWindow;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyRunWindowPropertiesTest {

    @Test
    void convertsConfigurationToRunWindow() {
        StrategyRunWindowProperties properties = new StrategyRunWindowProperties(
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
                LocalTime.of(9, 0),
                LocalTime.of(15, 30)
        );

        StrategyRunWindow runWindow = properties.toRunWindow();

        assertThat(runWindow.allowedDays())
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
        assertThat(runWindow.startsAt()).isEqualTo(LocalTime.of(9, 0));
        assertThat(runWindow.endsAt()).isEqualTo(LocalTime.of(15, 30));
    }

    @Test
    void copiesAllowedDays() {
        Set<DayOfWeek> source = EnumSet.of(DayOfWeek.MONDAY);
        StrategyRunWindowProperties properties = new StrategyRunWindowProperties(
                source,
                LocalTime.of(9, 0),
                LocalTime.of(15, 30)
        );

        source.clear();

        assertThat(properties.allowedDays()).containsExactly(DayOfWeek.MONDAY);
        assertThatThrownBy(() -> properties.allowedDays().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
