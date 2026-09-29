package com.stock.strategy.analysis.volatility.atr;

import com.stock.strategy.indicator.volatility.atr.AverageTrueRange;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

import java.util.Objects;

public record AverageTrueRangeAnalysisResult(
        InvestmentStrategyIdentity strategyIdentity,
        String symbol,
        AverageTrueRangeAnalysisStatus status,
        int requiredBarCount,
        int availableBarCount,
        AverageTrueRange averageTrueRange
) {
    public AverageTrueRangeAnalysisResult {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        Objects.requireNonNull(status, "status must not be null.");
        if (requiredBarCount < 2) {
            throw new IllegalArgumentException(
                    "requiredBarCount must be at least 2."
            );
        }
        if (availableBarCount < 0) {
            throw new IllegalArgumentException(
                    "availableBarCount must not be negative."
            );
        }

        if (status == AverageTrueRangeAnalysisStatus.ANALYZED) {
            validateAnalyzedResult(
                    symbol,
                    requiredBarCount,
                    availableBarCount,
                    averageTrueRange
            );
        } else {
            validateInsufficientDataResult(
                    requiredBarCount,
                    availableBarCount,
                    averageTrueRange
            );
        }
    }

    public static AverageTrueRangeAnalysisResult analyzed(
            InvestmentStrategyIdentity strategyIdentity,
            int availableBarCount,
            AverageTrueRange averageTrueRange
    ) {
        Objects.requireNonNull(
                averageTrueRange,
                "averageTrueRange must not be null."
        );
        return new AverageTrueRangeAnalysisResult(
                strategyIdentity,
                averageTrueRange.symbol(),
                AverageTrueRangeAnalysisStatus.ANALYZED,
                Math.addExact(averageTrueRange.period(), 1),
                availableBarCount,
                averageTrueRange
        );
    }

    public static AverageTrueRangeAnalysisResult insufficientData(
            InvestmentStrategyIdentity strategyIdentity,
            String symbol,
            int requiredBarCount,
            int availableBarCount
    ) {
        return new AverageTrueRangeAnalysisResult(
                strategyIdentity,
                symbol,
                AverageTrueRangeAnalysisStatus.INSUFFICIENT_DATA,
                requiredBarCount,
                availableBarCount,
                null
        );
    }

    private static void validateAnalyzedResult(
            String symbol,
            int requiredBarCount,
            int availableBarCount,
            AverageTrueRange averageTrueRange
    ) {
        Objects.requireNonNull(
                averageTrueRange,
                "averageTrueRange must not be null."
        );
        if (!symbol.equals(averageTrueRange.symbol())) {
            throw new IllegalArgumentException(
                    "symbol must match average true range symbol."
            );
        }
        long expectedRequiredBarCount =
                (long) averageTrueRange.period() + 1;
        if (requiredBarCount != expectedRequiredBarCount) {
            throw new IllegalArgumentException(
                    "requiredBarCount must be one greater than average true "
                            + "range period."
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
            AverageTrueRange averageTrueRange
    ) {
        if (averageTrueRange != null) {
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
