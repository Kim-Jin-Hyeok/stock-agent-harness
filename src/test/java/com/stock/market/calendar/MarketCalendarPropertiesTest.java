package com.stock.market.calendar;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketCalendarPropertiesTest {

    @Test
    void copiesClosedDates() {
        LocalDate closedDate = LocalDate.of(2026, 1, 1);
        Set<LocalDate> source = new HashSet<>(Set.of(closedDate));
        MarketCalendarProperties properties = new MarketCalendarProperties(source);

        source.clear();

        assertThat(properties.closedDates()).containsExactly(closedDate);
        assertThatThrownBy(() -> properties.closedDates().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
