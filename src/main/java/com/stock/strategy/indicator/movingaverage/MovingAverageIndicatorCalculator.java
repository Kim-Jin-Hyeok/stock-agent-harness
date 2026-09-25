package com.stock.strategy.indicator.movingaverage;

import com.stock.market.price.history.DailyPriceHistory;

import java.util.Objects;
import java.util.Optional;

public class MovingAverageIndicatorCalculator {
    private final SimpleMovingAverageCalculator simpleMovingAverageCalculator;

    public MovingAverageIndicatorCalculator(
            SimpleMovingAverageCalculator simpleMovingAverageCalculator
    ) {
        this.simpleMovingAverageCalculator = Objects.requireNonNull(
                simpleMovingAverageCalculator,
                "simpleMovingAverageCalculator must not be null."
        );
    }

    public Optional<MovingAverageIndicator> calculate(
            DailyPriceHistory history,
            MovingAveragePeriods periods
    ) {
        Objects.requireNonNull(history, "history must not be null.");
        Objects.requireNonNull(periods, "periods must not be null.");

        Optional<SimpleMovingAverage> longMovingAverage =
                simpleMovingAverageCalculator.calculate(
                        history,
                        periods.longPeriod()
                );
        if (longMovingAverage.isEmpty()) {
            return Optional.empty();
        }

        SimpleMovingAverage shortMovingAverage =
                simpleMovingAverageCalculator.calculate(
                                history,
                                periods.shortPeriod()
                        )
                        .orElseThrow(() -> new IllegalStateException(
                                "Short moving average must be available when "
                                        + "long moving average is available."
                        ));
        SimpleMovingAverage calculatedLongMovingAverage =
                longMovingAverage.orElseThrow();

        return Optional.of(new MovingAverageIndicator(
                history.symbol(),
                calculatedLongMovingAverage.toTradingDate(),
                shortMovingAverage,
                calculatedLongMovingAverage
        ));
    }
}
