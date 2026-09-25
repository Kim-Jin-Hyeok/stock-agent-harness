package com.stock.strategy.analysis.movingaverage;

import com.stock.strategy.indicator.movingaverage.MovingAverageIndicator;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignal;
import com.stock.strategy.signal.movingaverage.MovingAverageTrend;

import java.util.Objects;

public record MovingAverageAnalysisResult(
        InvestmentStrategyIdentity strategyIdentity,
        String symbol,
        MovingAverageAnalysisStatus status,
        int requiredBarCount,
        int availableBarCount,
        MovingAverageIndicator previousIndicator,
        MovingAverageTrend previousTrend,
        MovingAverageIndicator indicator,
        MovingAverageTrend trend,
        MovingAverageCrossoverSignal crossoverSignal
) {
    public MovingAverageAnalysisResult {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        Objects.requireNonNull(status, "status must not be null.");
        if (requiredBarCount < 1) {
            throw new IllegalArgumentException(
                    "requiredBarCount must be at least 1."
            );
        }
        if (availableBarCount < 0) {
            throw new IllegalArgumentException(
                    "availableBarCount must not be negative."
            );
        }

        if (status == MovingAverageAnalysisStatus.ANALYZED) {
            validateAnalyzedResult(
                    symbol,
                    requiredBarCount,
                    availableBarCount,
                    previousIndicator,
                    previousTrend,
                    indicator,
                    trend,
                    crossoverSignal
            );
        } else {
            validateInsufficientDataResult(
                    requiredBarCount,
                    availableBarCount,
                    previousIndicator,
                    previousTrend,
                    indicator,
                    trend,
                    crossoverSignal
            );
        }
    }

    public static MovingAverageAnalysisResult analyzed(
            InvestmentStrategyIdentity strategyIdentity,
            int availableBarCount,
            MovingAverageIndicator previousIndicator,
            MovingAverageTrend previousTrend,
            MovingAverageIndicator indicator,
            MovingAverageTrend trend,
            MovingAverageCrossoverSignal crossoverSignal
    ) {
        Objects.requireNonNull(
                previousIndicator,
                "previousIndicator must not be null."
        );
        Objects.requireNonNull(indicator, "indicator must not be null.");
        return new MovingAverageAnalysisResult(
                strategyIdentity,
                indicator.symbol(),
                MovingAverageAnalysisStatus.ANALYZED,
                Math.addExact(
                        indicator.longMovingAverage().period(),
                        1
                ),
                availableBarCount,
                previousIndicator,
                previousTrend,
                indicator,
                trend,
                crossoverSignal
        );
    }

    public static MovingAverageAnalysisResult insufficientData(
            InvestmentStrategyIdentity strategyIdentity,
            String symbol,
            int requiredBarCount,
            int availableBarCount
    ) {
        return new MovingAverageAnalysisResult(
                strategyIdentity,
                symbol,
                MovingAverageAnalysisStatus.INSUFFICIENT_DATA,
                requiredBarCount,
                availableBarCount,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static void validateAnalyzedResult(
            String symbol,
            int requiredBarCount,
            int availableBarCount,
            MovingAverageIndicator previousIndicator,
            MovingAverageTrend previousTrend,
            MovingAverageIndicator indicator,
            MovingAverageTrend trend,
            MovingAverageCrossoverSignal crossoverSignal
    ) {
        Objects.requireNonNull(
                previousIndicator,
                "previousIndicator must not be null."
        );
        Objects.requireNonNull(
                previousTrend,
                "previousTrend must not be null."
        );
        Objects.requireNonNull(indicator, "indicator must not be null.");
        Objects.requireNonNull(trend, "trend must not be null.");
        Objects.requireNonNull(
                crossoverSignal,
                "crossoverSignal must not be null."
        );
        if (!symbol.equals(previousIndicator.symbol())
                || !symbol.equals(indicator.symbol())) {
            throw new IllegalArgumentException(
                    "symbol must match indicator symbols."
            );
        }
        if (previousIndicator.shortMovingAverage().period()
                != indicator.shortMovingAverage().period()
                || previousIndicator.longMovingAverage().period()
                != indicator.longMovingAverage().period()) {
            throw new IllegalArgumentException(
                    "Previous and current indicator periods must match."
            );
        }
        if (requiredBarCount
                != indicator.longMovingAverage().period() + 1) {
            throw new IllegalArgumentException(
                    "requiredBarCount must be one greater than long moving "
                            + "average period."
            );
        }
        if (availableBarCount < requiredBarCount) {
            throw new IllegalArgumentException(
                    "Analyzed result requires enough available bars."
            );
        }
        if (!previousIndicator.asOfTradingDate()
                .isBefore(indicator.asOfTradingDate())) {
            throw new IllegalArgumentException(
                    "Previous indicator date must be before current "
                            + "indicator date."
            );
        }
    }

    private static void validateInsufficientDataResult(
            int requiredBarCount,
            int availableBarCount,
            MovingAverageIndicator previousIndicator,
            MovingAverageTrend previousTrend,
            MovingAverageIndicator indicator,
            MovingAverageTrend trend,
            MovingAverageCrossoverSignal crossoverSignal
    ) {
        if (previousIndicator != null
                || previousTrend != null
                || indicator != null
                || trend != null
                || crossoverSignal != null) {
            throw new IllegalArgumentException(
                    "Insufficient data result must not contain analysis."
            );
        }
        if (availableBarCount >= requiredBarCount) {
            throw new IllegalArgumentException(
                    "Insufficient data result requires fewer available bars "
                            + "than required bars."
            );
        }
    }
}
