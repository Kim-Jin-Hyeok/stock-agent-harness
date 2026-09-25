package com.stock.strategy.indicator.movingaverage;

public record MovingAveragePeriods(
        int shortPeriod,
        int longPeriod
) {
    public MovingAveragePeriods {
        if (shortPeriod < 1) {
            throw new IllegalArgumentException(
                    "shortPeriod must be at least 1."
            );
        }
        if (longPeriod <= shortPeriod) {
            throw new IllegalArgumentException(
                    "longPeriod must be greater than shortPeriod."
            );
        }
    }
}
