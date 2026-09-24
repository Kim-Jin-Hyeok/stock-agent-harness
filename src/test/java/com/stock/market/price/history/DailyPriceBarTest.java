package com.stock.market.price.history;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyPriceBarTest {
    private static final LocalDate TRADING_DATE = LocalDate.of(2026, 9, 23);

    @Test
    void createsBarWithValidValues() {
        DailyPriceBar bar = new DailyPriceBar(
                TRADING_DATE,
                100_000L,
                110_000L,
                95_000L,
                105_000L,
                1_000_000L
        );

        assertThat(bar.tradingDate()).isEqualTo(TRADING_DATE);
        assertThat(bar.openPriceKrw()).isEqualTo(100_000L);
        assertThat(bar.highPriceKrw()).isEqualTo(110_000L);
        assertThat(bar.lowPriceKrw()).isEqualTo(95_000L);
        assertThat(bar.closePriceKrw()).isEqualTo(105_000L);
        assertThat(bar.volume()).isEqualTo(1_000_000L);
    }

    @Test
    void rejectsNullTradingDate() {
        assertThatThrownBy(() -> new DailyPriceBar(
                null,
                100_000L,
                110_000L,
                95_000L,
                105_000L,
                1_000_000L
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tradingDate must not be null.");
    }

    @Test
    void rejectsNonPositivePrices() {
        assertThatThrownBy(() -> barWithPrices(0L, 110_000L, 95_000L, 105_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("openPriceKrw must be positive.");
        assertThatThrownBy(() -> barWithPrices(100_000L, 0L, 95_000L, 105_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("highPriceKrw must be positive.");
        assertThatThrownBy(() -> barWithPrices(100_000L, 110_000L, 0L, 105_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lowPriceKrw must be positive.");
        assertThatThrownBy(() -> barWithPrices(100_000L, 110_000L, 95_000L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("closePriceKrw must be positive.");
    }

    @Test
    void rejectsHighPriceLowerThanAnotherPrice() {
        assertThatThrownBy(() -> barWithPrices(
                100_000L,
                104_000L,
                95_000L,
                105_000L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("highPriceKrw must not be lower than another price.");
    }

    @Test
    void rejectsLowPriceHigherThanAnotherPrice() {
        assertThatThrownBy(() -> barWithPrices(
                100_000L,
                110_000L,
                101_000L,
                105_000L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lowPriceKrw must not be higher than another price.");
    }

    @Test
    void rejectsNegativeVolume() {
        assertThatThrownBy(() -> new DailyPriceBar(
                TRADING_DATE,
                100_000L,
                110_000L,
                95_000L,
                105_000L,
                -1L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("volume must not be negative.");
    }

    private DailyPriceBar barWithPrices(
            long openPriceKrw,
            long highPriceKrw,
            long lowPriceKrw,
            long closePriceKrw
    ) {
        return new DailyPriceBar(
                TRADING_DATE,
                openPriceKrw,
                highPriceKrw,
                lowPriceKrw,
                closePriceKrw,
                1_000_000L
        );
    }
}
