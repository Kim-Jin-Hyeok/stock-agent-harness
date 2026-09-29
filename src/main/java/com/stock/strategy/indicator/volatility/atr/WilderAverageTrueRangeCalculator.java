package com.stock.strategy.indicator.volatility.atr;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class WilderAverageTrueRangeCalculator {
    private static final int CALCULATION_SCALE = 8;
    private static final int OUTPUT_SCALE = 2;

    public Optional<AverageTrueRange> calculate(
            DailyPriceHistory history,
            int period
    ) {
        Objects.requireNonNull(history, "history must not be null.");
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive.");
        }

        List<DailyPriceBar> bars = history.bars();
        if (bars.size() <= period) {
            return Optional.empty();
        }

        BigDecimal averageTrueRange = initialAverageTrueRange(bars, period);
        BigDecimal periodValue = BigDecimal.valueOf(period);
        BigDecimal previousWeight = BigDecimal.valueOf(period - 1L);

        for (int index = period + 1; index < bars.size(); index++) {
            BigDecimal currentTrueRange = BigDecimal.valueOf(
                    calculateTrueRange(bars.get(index - 1), bars.get(index))
            );
            averageTrueRange = averageTrueRange
                    .multiply(previousWeight)
                    .add(currentTrueRange)
                    .divide(
                            periodValue,
                            CALCULATION_SCALE,
                            RoundingMode.HALF_UP
                    );
        }

        return Optional.of(new AverageTrueRange(
                history.symbol(),
                period,
                averageTrueRange.setScale(OUTPUT_SCALE, RoundingMode.HALF_UP),
                bars.get(1).tradingDate(),
                bars.getLast().tradingDate()
        ));
    }

    private BigDecimal initialAverageTrueRange(
            List<DailyPriceBar> bars,
            int period
    ) {
        BigDecimal trueRangeTotal = BigDecimal.ZERO;
        for (int index = 1; index <= period; index++) {
            long trueRange = calculateTrueRange(
                    bars.get(index - 1),
                    bars.get(index)
            );
            trueRangeTotal = trueRangeTotal.add(BigDecimal.valueOf(trueRange));
        }

        return trueRangeTotal.divide(
                BigDecimal.valueOf(period),
                CALCULATION_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private long calculateTrueRange(
            DailyPriceBar previousBar,
            DailyPriceBar currentBar
    ) {
        long intradayRange = currentBar.highPriceKrw()
                - currentBar.lowPriceKrw();
        long highToPreviousClose = Math.abs(
                currentBar.highPriceKrw() - previousBar.closePriceKrw()
        );
        long lowToPreviousClose = Math.abs(
                currentBar.lowPriceKrw() - previousBar.closePriceKrw()
        );

        return Math.max(
                intradayRange,
                Math.max(highToPreviousClose, lowToPreviousClose)
        );
    }
}
