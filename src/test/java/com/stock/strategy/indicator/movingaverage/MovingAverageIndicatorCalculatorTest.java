package com.stock.strategy.indicator.movingaverage;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovingAverageIndicatorCalculatorTest {
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 9, 1);

    private final MovingAverageIndicatorCalculator calculator =
            new MovingAverageIndicatorCalculator(
                    new SimpleMovingAverageCalculator()
            );

    @Test
    void calculatesShortAndLongMovingAverages() {
        DailyPriceHistory history = history(20);

        MovingAverageIndicator indicator = calculator.calculate(
                        history,
                        new MovingAveragePeriods(5, 20)
                )
                .orElseThrow();

        assertThat(indicator.symbol()).isEqualTo("005930");
        assertThat(indicator.asOfTradingDate())
                .isEqualTo(FIRST_DATE.plusDays(19));
        assertThat(indicator.shortMovingAverage().averagePriceKrw())
                .isEqualByComparingTo("18000.00");
        assertThat(indicator.shortMovingAverage().fromTradingDate())
                .isEqualTo(FIRST_DATE.plusDays(15));
        assertThat(indicator.longMovingAverage().averagePriceKrw())
                .isEqualByComparingTo("10500.00");
        assertThat(indicator.longMovingAverage().fromTradingDate())
                .isEqualTo(FIRST_DATE);
    }

    @Test
    void returnsEmptyWhenHistoryIsShorterThanLongPeriod() {
        DailyPriceHistory history = history(19);

        Optional<MovingAverageIndicator> result = calculator.calculate(
                history,
                new MovingAveragePeriods(5, 20)
        );

        assertThat(result).isEmpty();
    }

    @Test
    void rejectsNullInputs() {
        DailyPriceHistory history = history(20);
        MovingAveragePeriods periods = new MovingAveragePeriods(5, 20);

        assertThatThrownBy(() -> calculator.calculate(null, periods))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("history must not be null.");
        assertThatThrownBy(() -> calculator.calculate(history, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("periods must not be null.");
    }

    private DailyPriceHistory history(int size) {
        List<DailyPriceBar> bars = IntStream.rangeClosed(1, size)
                .mapToObj(index -> bar(
                        FIRST_DATE.plusDays(index - 1L),
                        index * 1_000L
                ))
                .toList();
        return new DailyPriceHistory("005930", bars);
    }

    private DailyPriceBar bar(
            LocalDate tradingDate,
            long closePriceKrw
    ) {
        return new DailyPriceBar(
                tradingDate,
                closePriceKrw,
                closePriceKrw,
                closePriceKrw,
                closePriceKrw,
                1_000L
        );
    }
}
