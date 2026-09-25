package com.stock.strategy.indicator.movingaverage;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleMovingAverageCalculatorTest {
    private final SimpleMovingAverageCalculator calculator =
            new SimpleMovingAverageCalculator();

    @Test
    void calculatesAverageFromLatestPeriodClosingPrices() {
        DailyPriceHistory history = history(
                bar(1, 10_000L),
                bar(2, 20_000L),
                bar(3, 30_000L),
                bar(4, 40_000L)
        );

        Optional<SimpleMovingAverage> result = calculator.calculate(
                history,
                3
        );

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().symbol()).isEqualTo("005930");
        assertThat(result.orElseThrow().period()).isEqualTo(3);
        assertThat(result.orElseThrow().averagePriceKrw())
                .isEqualByComparingTo("30000.00");
        assertThat(result.orElseThrow().fromTradingDate())
                .isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(result.orElseThrow().toTradingDate())
                .isEqualTo(LocalDate.of(2026, 9, 4));
    }

    @Test
    void roundsAveragePriceToTwoDecimalPlaces() {
        DailyPriceHistory history = history(
                bar(1, 100L),
                bar(2, 100L),
                bar(3, 102L)
        );

        SimpleMovingAverage result = calculator.calculate(history, 3)
                .orElseThrow();

        assertThat(result.averagePriceKrw())
                .isEqualByComparingTo("100.67");
    }

    @Test
    void returnsEmptyWhenHistoryIsShorterThanPeriod() {
        DailyPriceHistory history = history(
                bar(1, 10_000L),
                bar(2, 20_000L)
        );

        Optional<SimpleMovingAverage> result = calculator.calculate(
                history,
                3
        );

        assertThat(result).isEmpty();
    }

    @Test
    void rejectsNonPositivePeriod() {
        DailyPriceHistory history = history(bar(1, 10_000L));

        assertThatThrownBy(() -> calculator.calculate(history, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("period must be positive.");
    }

    @Test
    void rejectsNullHistory() {
        assertThatThrownBy(() -> calculator.calculate(null, 5))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("history must not be null.");
    }

    private DailyPriceHistory history(DailyPriceBar... bars) {
        return new DailyPriceHistory("005930", List.of(bars));
    }

    private DailyPriceBar bar(int dayOfMonth, long closePriceKrw) {
        return new DailyPriceBar(
                LocalDate.of(2026, 9, dayOfMonth),
                closePriceKrw,
                closePriceKrw,
                closePriceKrw,
                closePriceKrw,
                1_000L
        );
    }
}
