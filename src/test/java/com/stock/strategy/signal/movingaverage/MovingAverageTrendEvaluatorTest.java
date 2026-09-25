package com.stock.strategy.signal.movingaverage;

import com.stock.strategy.indicator.movingaverage.MovingAverageIndicator;
import com.stock.strategy.indicator.movingaverage.SimpleMovingAverage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovingAverageTrendEvaluatorTest {
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 9, 24);

    private final MovingAverageTrendEvaluator evaluator =
            new MovingAverageTrendEvaluator();

    @Test
    void returnsUptrendWhenShortMovingAverageIsHigher() {
        MovingAverageIndicator indicator = indicator(
                "71000.00",
                "70000.00"
        );

        MovingAverageTrend trend = evaluator.evaluate(indicator);

        assertThat(trend).isEqualTo(MovingAverageTrend.UPTREND);
    }

    @Test
    void returnsDowntrendWhenShortMovingAverageIsLower() {
        MovingAverageIndicator indicator = indicator(
                "69000.00",
                "70000.00"
        );

        MovingAverageTrend trend = evaluator.evaluate(indicator);

        assertThat(trend).isEqualTo(MovingAverageTrend.DOWNTREND);
    }

    @Test
    void returnsFlatWhenPricesHaveEqualValuesWithDifferentScales() {
        MovingAverageIndicator indicator = indicator(
                "70000.0",
                "70000.00"
        );

        MovingAverageTrend trend = evaluator.evaluate(indicator);

        assertThat(trend).isEqualTo(MovingAverageTrend.FLAT);
    }

    @Test
    void rejectsNullIndicator() {
        assertThatThrownBy(() -> evaluator.evaluate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("indicator must not be null.");
    }

    private MovingAverageIndicator indicator(
            String shortAveragePriceKrw,
            String longAveragePriceKrw
    ) {
        return new MovingAverageIndicator(
                "005930",
                AS_OF_DATE,
                movingAverage(5, shortAveragePriceKrw),
                movingAverage(20, longAveragePriceKrw)
        );
    }

    private SimpleMovingAverage movingAverage(
            int period,
            String averagePriceKrw
    ) {
        return new SimpleMovingAverage(
                "005930",
                period,
                new BigDecimal(averagePriceKrw),
                AS_OF_DATE.minusDays(period - 1L),
                AS_OF_DATE
        );
    }
}
