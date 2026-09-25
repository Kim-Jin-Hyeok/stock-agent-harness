package com.stock.strategy.indicator.movingaverage;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MovingAverageIndicatorTest {
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 9, 24);

    @Test
    void rejectsDifferentMovingAverageSymbol() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MovingAverageIndicator(
                        "005930",
                        AS_OF_DATE,
                        movingAverage("005930", 5, AS_OF_DATE),
                        movingAverage("000660", 20, AS_OF_DATE)
                ))
                .withMessage(
                        "Moving averages must have the same symbol."
                );
    }

    @Test
    void rejectsShortPeriodNotLessThanLongPeriod() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MovingAverageIndicator(
                        "005930",
                        AS_OF_DATE,
                        movingAverage("005930", 20, AS_OF_DATE),
                        movingAverage("005930", 20, AS_OF_DATE)
                ))
                .withMessage(
                        "Short moving average period must be less than long "
                                + "moving average period."
                );
    }

    @Test
    void rejectsDifferentMovingAverageTradingDate() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MovingAverageIndicator(
                        "005930",
                        AS_OF_DATE,
                        movingAverage("005930", 5, AS_OF_DATE.minusDays(1)),
                        movingAverage("005930", 20, AS_OF_DATE)
                ))
                .withMessage(
                        "Moving averages must have the same toTradingDate."
                );
    }

    @Test
    void rejectsAsOfDateDifferentFromMovingAverageTradingDate() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MovingAverageIndicator(
                        "005930",
                        AS_OF_DATE.minusDays(1),
                        movingAverage("005930", 5, AS_OF_DATE),
                        movingAverage("005930", 20, AS_OF_DATE)
                ))
                .withMessage(
                        "asOfTradingDate must match moving average "
                                + "toTradingDate."
                );
    }

    private SimpleMovingAverage movingAverage(
            String symbol,
            int period,
            LocalDate toTradingDate
    ) {
        return new SimpleMovingAverage(
                symbol,
                period,
                new BigDecimal("70000.00"),
                toTradingDate.minusDays(period - 1L),
                toTradingDate
        );
    }
}
