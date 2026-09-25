package com.stock.strategy.analysis.movingaverage;

import com.stock.strategy.indicator.movingaverage.MovingAverageIndicator;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.signal.movingaverage.MovingAverageTrend;

import java.util.Objects;

public record MovingAverageAnalysisResult(
        InvestmentStrategyIdentity strategyIdentity,
        String symbol,
        MovingAverageAnalysisStatus status,
        int requiredBarCount,
        int availableBarCount,
        MovingAverageIndicator indicator,
        MovingAverageTrend trend
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
                    indicator,
                    trend
            );
        } else {
            validateInsufficientDataResult(
                    requiredBarCount,
                    availableBarCount,
                    indicator,
                    trend
            );
        }
    }

    public static MovingAverageAnalysisResult analyzed(
            InvestmentStrategyIdentity strategyIdentity,
            int availableBarCount,
            MovingAverageIndicator indicator,
            MovingAverageTrend trend
    ) {
        Objects.requireNonNull(indicator, "indicator must not be null.");
        return new MovingAverageAnalysisResult(
                strategyIdentity,
                indicator.symbol(),
                MovingAverageAnalysisStatus.ANALYZED,
                indicator.longMovingAverage().period(),
                availableBarCount,
                indicator,
                trend
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
                null
        );
    }

    private static void validateAnalyzedResult(
            String symbol,
            int requiredBarCount,
            int availableBarCount,
            MovingAverageIndicator indicator,
            MovingAverageTrend trend
    ) {
        Objects.requireNonNull(indicator, "indicator must not be null.");
        Objects.requireNonNull(trend, "trend must not be null.");
        if (!symbol.equals(indicator.symbol())) {
            throw new IllegalArgumentException(
                    "symbol must match indicator symbol."
            );
        }
        if (requiredBarCount != indicator.longMovingAverage().period()) {
            throw new IllegalArgumentException(
                    "requiredBarCount must match long moving average period."
            );
        }
        if (availableBarCount < requiredBarCount) {
            throw new IllegalArgumentException(
                    "Analyzed result requires enough available bars."
            );
        }
    }

    private static void validateInsufficientDataResult(
            int requiredBarCount,
            int availableBarCount,
            MovingAverageIndicator indicator,
            MovingAverageTrend trend
    ) {
        if (indicator != null || trend != null) {
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
