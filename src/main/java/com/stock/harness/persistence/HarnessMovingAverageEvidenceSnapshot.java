package com.stock.harness.persistence;

import com.stock.agent.evidence.movingaverage.MovingAverageDecisionEvidence;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisResult;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisStatus;
import com.stock.strategy.indicator.movingaverage.MovingAverageIndicator;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignal;
import com.stock.strategy.signal.movingaverage.MovingAverageTrend;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record HarnessMovingAverageEvidenceSnapshot(
        MovingAverageAnalysisStatus status,
        String symbol,
        int requiredBarCount,
        int availableBarCount,
        MovingAverageTrend trend,
        Integer shortPeriod,
        BigDecimal shortAveragePriceKrw,
        Integer longPeriod,
        BigDecimal longAveragePriceKrw,
        LocalDate asOfTradingDate,
        Long currentPriceKrw,
        CurrentPriceLookupSource currentPriceSource,
        MovingAverageTrend previousTrend,
        BigDecimal previousShortAveragePriceKrw,
        BigDecimal previousLongAveragePriceKrw,
        LocalDate previousAsOfTradingDate,
        MovingAverageCrossoverSignal crossoverSignal
) {
    public HarnessMovingAverageEvidenceSnapshot(
            MovingAverageAnalysisStatus status,
            String symbol,
            int requiredBarCount,
            int availableBarCount,
            MovingAverageTrend trend,
            Integer shortPeriod,
            BigDecimal shortAveragePriceKrw,
            Integer longPeriod,
            BigDecimal longAveragePriceKrw,
            LocalDate asOfTradingDate,
            Long currentPriceKrw,
            CurrentPriceLookupSource currentPriceSource
    ) {
        this(
                status,
                symbol,
                requiredBarCount,
                availableBarCount,
                trend,
                shortPeriod,
                shortAveragePriceKrw,
                longPeriod,
                longAveragePriceKrw,
                asOfTradingDate,
                currentPriceKrw,
                currentPriceSource,
                null,
                null,
                null,
                null,
                null
        );
    }

    public HarnessMovingAverageEvidenceSnapshot {
        Objects.requireNonNull(status, "status must not be null.");
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
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
            validateAnalyzedSnapshot(
                    requiredBarCount,
                    availableBarCount,
                    trend,
                    shortPeriod,
                    shortAveragePriceKrw,
                    longPeriod,
                    longAveragePriceKrw,
                    asOfTradingDate,
                    currentPriceKrw,
                    currentPriceSource,
                    previousTrend,
                    previousShortAveragePriceKrw,
                    previousLongAveragePriceKrw,
                    previousAsOfTradingDate,
                    crossoverSignal
            );
        } else {
            validateInsufficientDataSnapshot(
                    requiredBarCount,
                    availableBarCount,
                    trend,
                    shortPeriod,
                    shortAveragePriceKrw,
                    longPeriod,
                    longAveragePriceKrw,
                    asOfTradingDate,
                    currentPriceKrw,
                    currentPriceSource,
                    previousTrend,
                    previousShortAveragePriceKrw,
                    previousLongAveragePriceKrw,
                    previousAsOfTradingDate,
                    crossoverSignal
            );
        }
    }

    public static HarnessMovingAverageEvidenceSnapshot from(
            MovingAverageDecisionEvidence evidence
    ) {
        Objects.requireNonNull(evidence, "evidence must not be null.");
        MovingAverageAnalysisResult analysis = evidence.analysis();
        if (analysis.status()
                == MovingAverageAnalysisStatus.INSUFFICIENT_DATA) {
            return new HarnessMovingAverageEvidenceSnapshot(
                    analysis.status(),
                    analysis.symbol(),
                    analysis.requiredBarCount(),
                    analysis.availableBarCount(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        MovingAverageIndicator indicator = analysis.indicator();
        MovingAverageIndicator previousIndicator =
                analysis.previousIndicator();
        return new HarnessMovingAverageEvidenceSnapshot(
                analysis.status(),
                analysis.symbol(),
                analysis.requiredBarCount(),
                analysis.availableBarCount(),
                analysis.trend(),
                indicator.shortMovingAverage().period(),
                indicator.shortMovingAverage().averagePriceKrw(),
                indicator.longMovingAverage().period(),
                indicator.longMovingAverage().averagePriceKrw(),
                indicator.asOfTradingDate(),
                evidence.currentPriceKrw(),
                evidence.currentPriceSource(),
                analysis.previousTrend(),
                previousIndicator.shortMovingAverage().averagePriceKrw(),
                previousIndicator.longMovingAverage().averagePriceKrw(),
                previousIndicator.asOfTradingDate(),
                analysis.crossoverSignal()
        );
    }

    private static void validateAnalyzedSnapshot(
            int requiredBarCount,
            int availableBarCount,
            MovingAverageTrend trend,
            Integer shortPeriod,
            BigDecimal shortAveragePriceKrw,
            Integer longPeriod,
            BigDecimal longAveragePriceKrw,
            LocalDate asOfTradingDate,
            Long currentPriceKrw,
            CurrentPriceLookupSource currentPriceSource,
            MovingAverageTrend previousTrend,
            BigDecimal previousShortAveragePriceKrw,
            BigDecimal previousLongAveragePriceKrw,
            LocalDate previousAsOfTradingDate,
            MovingAverageCrossoverSignal crossoverSignal
    ) {
        Objects.requireNonNull(trend, "trend must not be null.");
        Objects.requireNonNull(shortPeriod, "shortPeriod must not be null.");
        Objects.requireNonNull(
                shortAveragePriceKrw,
                "shortAveragePriceKrw must not be null."
        );
        Objects.requireNonNull(longPeriod, "longPeriod must not be null.");
        Objects.requireNonNull(
                longAveragePriceKrw,
                "longAveragePriceKrw must not be null."
        );
        Objects.requireNonNull(
                asOfTradingDate,
                "asOfTradingDate must not be null."
        );
        Objects.requireNonNull(
                currentPriceSource,
                "currentPriceSource must not be null."
        );
        if (shortPeriod < 1 || longPeriod <= shortPeriod) {
            throw new IllegalArgumentException(
                    "Moving average periods are invalid."
            );
        }
        if (requiredBarCount != longPeriod
                && requiredBarCount != longPeriod + 1) {
            throw new IllegalArgumentException(
                    "requiredBarCount must match the legacy trend analysis "
                            + "or crossover analysis requirement."
            );
        }
        if (availableBarCount < requiredBarCount) {
            throw new IllegalArgumentException(
                    "Analyzed snapshot requires enough available bars."
            );
        }
        if (shortAveragePriceKrw.signum() <= 0
                || longAveragePriceKrw.signum() <= 0
                || currentPriceKrw == null
                || currentPriceKrw <= 0) {
            throw new IllegalArgumentException(
                    "Analyzed snapshot prices must be positive."
            );
        }
        validateCrossoverSnapshot(
                trend,
                asOfTradingDate,
                previousTrend,
                previousShortAveragePriceKrw,
                previousLongAveragePriceKrw,
                previousAsOfTradingDate,
                crossoverSignal
        );
    }

    private static void validateInsufficientDataSnapshot(
            int requiredBarCount,
            int availableBarCount,
            MovingAverageTrend trend,
            Integer shortPeriod,
            BigDecimal shortAveragePriceKrw,
            Integer longPeriod,
            BigDecimal longAveragePriceKrw,
            LocalDate asOfTradingDate,
            Long currentPriceKrw,
            CurrentPriceLookupSource currentPriceSource,
            MovingAverageTrend previousTrend,
            BigDecimal previousShortAveragePriceKrw,
            BigDecimal previousLongAveragePriceKrw,
            LocalDate previousAsOfTradingDate,
            MovingAverageCrossoverSignal crossoverSignal
    ) {
        if (availableBarCount >= requiredBarCount) {
            throw new IllegalArgumentException(
                    "Insufficient data snapshot requires fewer available "
                            + "bars than required bars."
            );
        }
        if (trend != null
                || shortPeriod != null
                || shortAveragePriceKrw != null
                || longPeriod != null
                || longAveragePriceKrw != null
                || asOfTradingDate != null
                || currentPriceKrw != null
                || currentPriceSource != null
                || previousTrend != null
                || previousShortAveragePriceKrw != null
                || previousLongAveragePriceKrw != null
                || previousAsOfTradingDate != null
                || crossoverSignal != null) {
            throw new IllegalArgumentException(
                    "Insufficient data snapshot must not contain analysis."
            );
        }
    }

    private static void validateCrossoverSnapshot(
            MovingAverageTrend trend,
            LocalDate asOfTradingDate,
            MovingAverageTrend previousTrend,
            BigDecimal previousShortAveragePriceKrw,
            BigDecimal previousLongAveragePriceKrw,
            LocalDate previousAsOfTradingDate,
            MovingAverageCrossoverSignal crossoverSignal
    ) {
        int populatedFieldCount = 0;
        populatedFieldCount += previousTrend == null ? 0 : 1;
        populatedFieldCount += previousShortAveragePriceKrw == null ? 0 : 1;
        populatedFieldCount += previousLongAveragePriceKrw == null ? 0 : 1;
        populatedFieldCount += previousAsOfTradingDate == null ? 0 : 1;
        populatedFieldCount += crossoverSignal == null ? 0 : 1;

        if (populatedFieldCount == 0) {
            return;
        }
        if (populatedFieldCount < 5) {
            throw new IllegalArgumentException(
                    "Crossover snapshot fields must be all present or all null."
            );
        }
        if (previousShortAveragePriceKrw.signum() <= 0
                || previousLongAveragePriceKrw.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Previous moving average prices must be positive."
            );
        }
        if (!previousAsOfTradingDate.isBefore(asOfTradingDate)) {
            throw new IllegalArgumentException(
                    "Previous indicator date must be before current "
                            + "indicator date."
            );
        }

        MovingAverageCrossoverSignal expectedSignal =
                expectedCrossoverSignal(previousTrend, trend);
        if (crossoverSignal != expectedSignal) {
            throw new IllegalArgumentException(
                    "crossoverSignal does not match trend transition."
            );
        }
    }

    private static MovingAverageCrossoverSignal expectedCrossoverSignal(
            MovingAverageTrend previousTrend,
            MovingAverageTrend trend
    ) {
        if (trend == MovingAverageTrend.UPTREND
                && previousTrend != MovingAverageTrend.UPTREND) {
            return MovingAverageCrossoverSignal.GOLDEN_CROSS;
        }
        if (trend == MovingAverageTrend.DOWNTREND
                && previousTrend != MovingAverageTrend.DOWNTREND) {
            return MovingAverageCrossoverSignal.DEAD_CROSS;
        }
        return MovingAverageCrossoverSignal.NONE;
    }
}
