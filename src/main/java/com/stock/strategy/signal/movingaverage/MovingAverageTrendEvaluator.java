package com.stock.strategy.signal.movingaverage;

import com.stock.strategy.indicator.movingaverage.MovingAverageIndicator;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
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
