package com.stock.strategy.indicator.volatility.atr;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.TradingVenueScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WilderAverageTrueRangeCalculatorTest {
    private final WilderAverageTrueRangeCalculator calculator =
            new WilderAverageTrueRangeCalculator();

    @Test
    void calculatesInitialAverageFromFirstPeriodTrueRanges() {
        DailyPriceHistory history = history(
                bar(1, 100L, 100L, 100L, 100L),
                bar(2, 100L, 110L, 95L, 105L),
                bar(3, 105L, 112L, 100L, 110L),
                bar(4, 110L, 115L, 104L, 108L)
        );

        AverageTrueRange result = calculator.calculate(history, 3)
                .orElseThrow();

        assertThat(result.symbol()).isEqualTo("005930");
        assertThat(result.period()).isEqualTo(3);
        assertThat(result.averageTrueRangeKrw())
                .isEqualByComparingTo("12.67");
        assertThat(result.fromTradingDate())
                .isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(result.toTradingDate())
                .isEqualTo(LocalDate.of(2026, 9, 4));
    }

    @Test
    void appliesWilderSmoothingToRemainingTrueRanges() {
        DailyPriceHistory history = history(
                bar(1, 100L, 100L, 100L, 100L),
                bar(2, 100L, 110L, 95L, 105L),
                bar(3, 105L, 112L, 100L, 110L),
                bar(4, 110L, 115L, 104L, 108L),
                bar(5, 108L, 120L, 108L, 118L)
        );

        AverageTrueRange result = calculator.calculate(history, 3)
                .orElseThrow();

        assertThat(result.averageTrueRangeKrw())
                .isEqualByComparingTo("12.44");
        assertThat(result.toTradingDate())
                .isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 99_999_999_999L})
    void tradingMetadataDoesNotChangeWilderAtr(Long value) {
        DailyPriceHistory legacy = history(
                bar(1, 100L, 100L, 100L, 100L),
                bar(2, 100L, 110L, 95L, 105L),
                bar(3, 105L, 112L, 100L, 110L),
                bar(4, 110L, 115L, 104L, 108L),
                bar(5, 108L, 120L, 108L, 118L)
        );
        DailyPriceHistory withMetadata = new DailyPriceHistory(legacy.symbol(),
                legacy.bars().stream().map(bar -> new DailyPriceBar(
                        bar.tradingDate(), bar.openPriceKrw(), bar.highPriceKrw(),
                        bar.lowPriceKrw(), bar.closePriceKrw(), bar.volume(),
                        value, TradingVenueScope.NXT)).toList());

        assertThat(calculator.calculate(withMetadata, 3))
                .isEqualTo(calculator.calculate(legacy, 3));
    }

    @Test
    void usesPreviousCloseForUpwardGapTrueRange() {
        DailyPriceHistory history = history(
                bar(1, 100L, 100L, 100L, 100L),
                bar(2, 120L, 125L, 118L, 123L)
        );

        AverageTrueRange result = calculator.calculate(history, 1)
                .orElseThrow();

        assertThat(result.averageTrueRangeKrw())
                .isEqualByComparingTo("25.00");
    }

    @Test
    void usesPreviousCloseForDownwardGapTrueRange() {
        DailyPriceHistory history = history(
                bar(1, 100L, 100L, 100L, 100L),
                bar(2, 80L, 82L, 75L, 78L)
        );

        AverageTrueRange result = calculator.calculate(history, 1)
                .orElseThrow();

        assertThat(result.averageTrueRangeKrw())
                .isEqualByComparingTo("25.00");
    }

    @Test
    void usesChronologicalOrderNormalizedByHistory() {
        DailyPriceHistory history = history(
                bar(4, 110L, 115L, 104L, 108L),
                bar(2, 100L, 110L, 95L, 105L),
                bar(1, 100L, 100L, 100L, 100L),
                bar(3, 105L, 112L, 100L, 110L)
        );

        AverageTrueRange result = calculator.calculate(history, 3)
                .orElseThrow();

        assertThat(result.averageTrueRangeKrw())
                .isEqualByComparingTo("12.67");
        assertThat(result.fromTradingDate())
                .isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(result.toTradingDate())
                .isEqualTo(LocalDate.of(2026, 9, 4));
    }

    @Test
    void returnsEmptyWhenHistoryCannotProducePeriodTrueRanges() {
        DailyPriceHistory history = history(
                bar(1, 100L, 100L, 100L, 100L),
                bar(2, 100L, 110L, 95L, 105L),
                bar(3, 105L, 112L, 100L, 110L)
        );

        Optional<AverageTrueRange> result = calculator.calculate(history, 3);

        assertThat(result).isEmpty();
    }

    @Test
    void rejectsNonPositivePeriod() {
        DailyPriceHistory history = history(
                bar(1, 100L, 100L, 100L, 100L)
        );

        assertThatThrownBy(() -> calculator.calculate(history, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("period must be positive.");
    }

    @Test
    void rejectsNullHistory() {
        assertThatThrownBy(() -> calculator.calculate(null, 14))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("history must not be null.");
    }

    private DailyPriceHistory history(DailyPriceBar... bars) {
        return new DailyPriceHistory("005930", List.of(bars));
    }

    private DailyPriceBar bar(
            int dayOfMonth,
            long openPriceKrw,
            long highPriceKrw,
            long lowPriceKrw,
            long closePriceKrw
    ) {
        return new DailyPriceBar(
                LocalDate.of(2026, 9, dayOfMonth),
                openPriceKrw,
                highPriceKrw,
                lowPriceKrw,
                closePriceKrw,
                1_000L
        );
    }
}
