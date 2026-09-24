package com.stock.market.price.history;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyPriceHistoryTest {

    @Test
    void sortsBarsByTradingDateAscending() {
        DailyPriceBar latest = bar(LocalDate.of(2026, 9, 23));
        DailyPriceBar earliest = bar(LocalDate.of(2026, 9, 21));
        DailyPriceBar middle = bar(LocalDate.of(2026, 9, 22));

        DailyPriceHistory history = new DailyPriceHistory(
                "005930",
                List.of(latest, earliest, middle)
        );

        assertThat(history.bars())
                .extracting(DailyPriceBar::tradingDate)
                .containsExactly(
                        earliest.tradingDate(),
                        middle.tradingDate(),
                        latest.tradingDate()
                );
    }

    @Test
    void copiesBarsAsImmutableList() {
        List<DailyPriceBar> source = new ArrayList<>();
        source.add(bar(LocalDate.of(2026, 9, 22)));

        DailyPriceHistory history = new DailyPriceHistory("005930", source);
        source.add(bar(LocalDate.of(2026, 9, 23)));

        assertThat(history.bars()).hasSize(1);
        assertThatThrownBy(() -> history.bars().add(
                bar(LocalDate.of(2026, 9, 24))
        )).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsBlankSymbol() {
        assertThatThrownBy(() -> new DailyPriceHistory(" ", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must not be blank.");
    }

    @Test
    void rejectsNullBars() {
        assertThatThrownBy(() -> new DailyPriceHistory("005930", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("bars must not be null.");
    }

    @Test
    void rejectsNullBar() {
        List<DailyPriceBar> bars = new ArrayList<>();
        bars.add(null);

        assertThatThrownBy(() -> new DailyPriceHistory("005930", bars))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("bar must not be null.");
    }

    @Test
    void rejectsDuplicateTradingDate() {
        LocalDate tradingDate = LocalDate.of(2026, 9, 23);

        assertThatThrownBy(() -> new DailyPriceHistory(
                "005930",
                List.of(bar(tradingDate), bar(tradingDate))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bars must not contain duplicate tradingDate.");
    }

    private DailyPriceBar bar(LocalDate tradingDate) {
        return new DailyPriceBar(
                tradingDate,
                100_000L,
                110_000L,
                95_000L,
                105_000L,
                1_000_000L
        );
    }
}
