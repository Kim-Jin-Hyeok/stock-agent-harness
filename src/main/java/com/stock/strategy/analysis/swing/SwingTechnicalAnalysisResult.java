package com.stock.strategy.analysis.swing;

import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisResult;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisStatus;
import com.stock.strategy.analysis.volatility.atr.AverageTrueRangeAnalysisResult;
import com.stock.strategy.analysis.volatility.atr.AverageTrueRangeAnalysisStatus;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

import java.util.Objects;

public record SwingTechnicalAnalysisResult(
        InvestmentStrategyIdentity strategyIdentity,
        String symbol,
        SwingTechnicalAnalysisStatus status,
        int requiredBarCount,
        int availableBarCount,
        MovingAverageAnalysisResult movingAverageAnalysis,
        AverageTrueRangeAnalysisResult averageTrueRangeAnalysis
) {
    public SwingTechnicalAnalysisResult {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        if (strategyIdentity.horizon() != InvestmentHorizon.SWING) {
            throw new IllegalArgumentException(
                    "strategyIdentity horizon must be SWING."
            );
        }
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
        Objects.requireNonNull(
                movingAverageAnalysis,
                "movingAverageAnalysis must not be null."
        );
        Objects.requireNonNull(
                averageTrueRangeAnalysis,
                "averageTrueRangeAnalysis must not be null."
        );

        validateAnalysisIdentity(
                strategyIdentity,
                movingAverageAnalysis,
                averageTrueRangeAnalysis
        );
        validateAnalysisSymbol(
                symbol,
                movingAverageAnalysis,
                averageTrueRangeAnalysis
        );
        validateBarCounts(
                requiredBarCount,
                availableBarCount,
                movingAverageAnalysis,
                averageTrueRangeAnalysis
        );
        validateStatus(
                status,
                movingAverageAnalysis,
                averageTrueRangeAnalysis
        );
    }

    public static SwingTechnicalAnalysisResult from(
            MovingAverageAnalysisResult movingAverageAnalysis,
            AverageTrueRangeAnalysisResult averageTrueRangeAnalysis
    ) {
        Objects.requireNonNull(
                movingAverageAnalysis,
                "movingAverageAnalysis must not be null."
        );
        Objects.requireNonNull(
                averageTrueRangeAnalysis,
                "averageTrueRangeAnalysis must not be null."
        );

        SwingTechnicalAnalysisStatus status =
                movingAverageAnalysis.status()
                        == MovingAverageAnalysisStatus.ANALYZED
                        && averageTrueRangeAnalysis.status()
                        == AverageTrueRangeAnalysisStatus.ANALYZED
                        ? SwingTechnicalAnalysisStatus.ANALYZED
                        : SwingTechnicalAnalysisStatus.INSUFFICIENT_DATA;
        return new SwingTechnicalAnalysisResult(
                movingAverageAnalysis.strategyIdentity(),
                movingAverageAnalysis.symbol(),
                status,
                Math.max(
                        movingAverageAnalysis.requiredBarCount(),
                        averageTrueRangeAnalysis.requiredBarCount()
                ),
                movingAverageAnalysis.availableBarCount(),
                movingAverageAnalysis,
                averageTrueRangeAnalysis
        );
    }

    private static void validateAnalysisIdentity(
            InvestmentStrategyIdentity strategyIdentity,
            MovingAverageAnalysisResult movingAverageAnalysis,
            AverageTrueRangeAnalysisResult averageTrueRangeAnalysis
    ) {
        if (!strategyIdentity.equals(movingAverageAnalysis.strategyIdentity())
                || !strategyIdentity.equals(
                        averageTrueRangeAnalysis.strategyIdentity()
                )) {
            throw new IllegalArgumentException(
                    "strategyIdentity must match analysis results."
            );
        }
    }

    private static void validateAnalysisSymbol(
            String symbol,
            MovingAverageAnalysisResult movingAverageAnalysis,
            AverageTrueRangeAnalysisResult averageTrueRangeAnalysis
    ) {
        if (!symbol.equals(movingAverageAnalysis.symbol())
                || !symbol.equals(averageTrueRangeAnalysis.symbol())) {
            throw new IllegalArgumentException(
                    "symbol must match analysis results."
            );
        }
    }

    private static void validateBarCounts(
            int requiredBarCount,
            int availableBarCount,
            MovingAverageAnalysisResult movingAverageAnalysis,
            AverageTrueRangeAnalysisResult averageTrueRangeAnalysis
    ) {
        int expectedRequiredBarCount = Math.max(
                movingAverageAnalysis.requiredBarCount(),
                averageTrueRangeAnalysis.requiredBarCount()
        );
        if (requiredBarCount != expectedRequiredBarCount) {
            throw new IllegalArgumentException(
                    "requiredBarCount must match the largest analysis "
                            + "requirement."
            );
        }
        if (availableBarCount != movingAverageAnalysis.availableBarCount()
                || availableBarCount
                != averageTrueRangeAnalysis.availableBarCount()) {
            throw new IllegalArgumentException(
                    "availableBarCount must match analysis results."
            );
        }
    }

    private static void validateStatus(
            SwingTechnicalAnalysisStatus status,
            MovingAverageAnalysisResult movingAverageAnalysis,
            AverageTrueRangeAnalysisResult averageTrueRangeAnalysis
    ) {
        boolean allAnalyzed = movingAverageAnalysis.status()
                == MovingAverageAnalysisStatus.ANALYZED
                && averageTrueRangeAnalysis.status()
                == AverageTrueRangeAnalysisStatus.ANALYZED;
        SwingTechnicalAnalysisStatus expectedStatus = allAnalyzed
                ? SwingTechnicalAnalysisStatus.ANALYZED
                : SwingTechnicalAnalysisStatus.INSUFFICIENT_DATA;
        if (status != expectedStatus) {
            throw new IllegalArgumentException(
                    "status must match analysis result statuses."
            );
        }
    }
}
