package com.stock.strategy.signal.movingaverage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovingAverageCrossoverSignalEvaluatorTest {
    private final MovingAverageCrossoverSignalEvaluator evaluator =
            new MovingAverageCrossoverSignalEvaluator();

    @Test
    void returnsGoldenCrossWhenDowntrendChangesToUptrend() {
        MovingAverageCrossoverSignal signal = evaluator.evaluate(
                MovingAverageTrend.DOWNTREND,
                MovingAverageTrend.UPTREND
        );

        assertThat(signal)
                .isEqualTo(MovingAverageCrossoverSignal.GOLDEN_CROSS);
    }

    @Test
    void returnsGoldenCrossWhenFlatChangesToUptrend() {
        MovingAverageCrossoverSignal signal = evaluator.evaluate(
                MovingAverageTrend.FLAT,
                MovingAverageTrend.UPTREND
        );

        assertThat(signal)
                .isEqualTo(MovingAverageCrossoverSignal.GOLDEN_CROSS);
    }

    @Test
    void returnsDeadCrossWhenUptrendChangesToDowntrend() {
        MovingAverageCrossoverSignal signal = evaluator.evaluate(
                MovingAverageTrend.UPTREND,
                MovingAverageTrend.DOWNTREND
        );

        assertThat(signal)
                .isEqualTo(MovingAverageCrossoverSignal.DEAD_CROSS);
    }

    @Test
    void returnsDeadCrossWhenFlatChangesToDowntrend() {
        MovingAverageCrossoverSignal signal = evaluator.evaluate(
                MovingAverageTrend.FLAT,
                MovingAverageTrend.DOWNTREND
        );

        assertThat(signal)
                .isEqualTo(MovingAverageCrossoverSignal.DEAD_CROSS);
    }

    @Test
    void returnsNoneWhenTrendDoesNotChange() {
        MovingAverageCrossoverSignal signal = evaluator.evaluate(
                MovingAverageTrend.UPTREND,
                MovingAverageTrend.UPTREND
        );

        assertThat(signal).isEqualTo(MovingAverageCrossoverSignal.NONE);
    }

    @Test
    void returnsNoneWhenCurrentTrendIsFlat() {
        MovingAverageCrossoverSignal signal = evaluator.evaluate(
                MovingAverageTrend.UPTREND,
                MovingAverageTrend.FLAT
        );

        assertThat(signal).isEqualTo(MovingAverageCrossoverSignal.NONE);
    }

    @Test
    void rejectsNullPreviousTrend() {
        assertThatThrownBy(() -> evaluator.evaluate(
                null,
                MovingAverageTrend.UPTREND
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("previousTrend must not be null.");
    }

    @Test
    void rejectsNullCurrentTrend() {
        assertThatThrownBy(() -> evaluator.evaluate(
                MovingAverageTrend.UPTREND,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("currentTrend must not be null.");
    }
}
