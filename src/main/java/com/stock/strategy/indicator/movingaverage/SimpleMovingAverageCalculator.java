package com.stock.strategy.indicator.movingaverage;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SimpleMovingAverageCalculator {
    private static final int PRICE_SCALE = 2;

    public Optional<SimpleMovingAverage> calculate(
            DailyPriceHistory history,
            int period
    ) {
        Objects.requireNonNull(history, "history must not be null.");
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive.");
        }

        List<DailyPriceBar> bars = history.bars();
        if (bars.size() < period) {
            return Optional.empty();
        }

        int fromIndex = bars.size() - period;
        List<DailyPriceBar> calculationBars = bars.subList(
                fromIndex,
                bars.size()
        );
        BigDecimal totalClosePriceKrw = calculationBars.stream()
                .map(DailyPriceBar::closePriceKrw)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averagePriceKrw = totalClosePriceKrw.divide(
                BigDecimal.valueOf(period),
                PRICE_SCALE,
                RoundingMode.HALF_UP
        );

        return Optional.of(new SimpleMovingAverage(
                history.symbol(),
                period,
                averagePriceKrw,
                calculationBars.getFirst().tradingDate(),
                calculationBars.getLast().tradingDate()
        ));
    }
}
