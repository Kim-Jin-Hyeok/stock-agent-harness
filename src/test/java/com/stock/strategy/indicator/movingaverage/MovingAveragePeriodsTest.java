package com.stock.strategy.indicator.movingaverage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MovingAveragePeriodsTest {

    @Test
    void rejectsNonPositiveShortPeriod() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MovingAveragePeriods(0, 20))
                .withMessage("shortPeriod must be at least 1.");
    }

    @Test
    void rejectsLongPeriodNotGreaterThanShortPeriod() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MovingAveragePeriods(20, 20))
                .withMessage(
                        "longPeriod must be greater than shortPeriod."
                );
    }
}
