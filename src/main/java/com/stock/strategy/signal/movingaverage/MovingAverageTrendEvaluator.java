package com.stock.strategy.signal.movingaverage;

import com.stock.strategy.indicator.movingaverage.MovingAverageIndicator;

import java.util.Objects;

public class MovingAverageTrendEvaluator {

    public MovingAverageTrend evaluate(MovingAverageIndicator indicator) {
        Objects.requireNonNull(indicator, "indicator must not be null.");

        int comparison = indicator.shortMovingAverage()
                .averagePriceKrw()
                .compareTo(
                        indicator.longMovingAverage().averagePriceKrw()
                );
        if (comparison > 0) {
            return MovingAverageTrend.UPTREND;
        }
        if (comparison < 0) {
            return MovingAverageTrend.DOWNTREND;
        }
        return MovingAverageTrend.FLAT;
    }
}
