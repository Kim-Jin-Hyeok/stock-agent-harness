package com.stock.strategy.signal.movingaverage;

import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class MovingAverageCrossoverSignalEvaluator {

    public MovingAverageCrossoverSignal evaluate(
            MovingAverageTrend previousTrend,
            MovingAverageTrend currentTrend
    ) {
        Objects.requireNonNull(
                previousTrend,
                "previousTrend must not be null."
        );
        Objects.requireNonNull(
                currentTrend,
                "currentTrend must not be null."
        );

        if (currentTrend == MovingAverageTrend.UPTREND
                && previousTrend != MovingAverageTrend.UPTREND) {
            return MovingAverageCrossoverSignal.GOLDEN_CROSS;
        }
        if (currentTrend == MovingAverageTrend.DOWNTREND
                && previousTrend != MovingAverageTrend.DOWNTREND) {
            return MovingAverageCrossoverSignal.DEAD_CROSS;
        }
        return MovingAverageCrossoverSignal.NONE;
    }
}
