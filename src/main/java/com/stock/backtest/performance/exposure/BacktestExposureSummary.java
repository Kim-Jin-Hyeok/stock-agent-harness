package com.stock.backtest.performance.exposure;

import com.stock.backtest.performance.equity.BacktestEquitySnapshot;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;

public record BacktestExposureSummary(
        int observationCount,
        int investedObservationCount,
        BigDecimal investedObservationRate,
        BigDecimal averagePositionRatio,
        BigDecimal maxPositionRatio
) {
    public BacktestExposureSummary {
        if (observationCount < 1) {
            throw new IllegalArgumentException(
                    "observationCount must be at least 1."
            );
        }
        if (investedObservationCount < 0
                || investedObservationCount > observationCount) {
            throw new IllegalArgumentException(
                    "investedObservationCount must be between 0 "
                            + "and observationCount."
            );
        }
        validateRate("investedObservationRate", investedObservationRate);
        validateRate("averagePositionRatio", averagePositionRatio);
        validateRate("maxPositionRatio", maxPositionRatio);
        BigDecimal expectedInvestedRate = BigDecimal.valueOf(
                investedObservationCount
        ).divide(BigDecimal.valueOf(observationCount), MathContext.DECIMAL128);
        if (investedObservationRate.compareTo(expectedInvestedRate) != 0) {
            throw new IllegalArgumentException(
                    "investedObservationRate must match observation counts."
            );
        }
        if (averagePositionRatio.compareTo(maxPositionRatio) > 0
                || averagePositionRatio.compareTo(investedObservationRate) > 0) {
            throw new IllegalArgumentException(
                    "averagePositionRatio must not exceed maxPositionRatio "
                            + "or investedObservationRate."
            );
        }
        if (investedObservationCount == 0) {
            if (averagePositionRatio.signum() != 0
                    || maxPositionRatio.signum() != 0) {
                throw new IllegalArgumentException(
                        "Cash-only exposure must have zero position ratios."
                );
            }
        } else if (averagePositionRatio.signum() == 0
                || maxPositionRatio.signum() == 0) {
            throw new IllegalArgumentException(
                    "Invested exposure must have positive position ratios."
            );
        }
    }

    public void validateAgainst(List<BacktestEquitySnapshot> equityCurve) {
        BacktestExposureSummary expected = BacktestExposureCalculator.calculate(
                equityCurve
        );
        if (observationCount != expected.observationCount()
                || investedObservationCount != expected.investedObservationCount()
                || investedObservationRate.compareTo(
                        expected.investedObservationRate()
                ) != 0
                || averagePositionRatio.compareTo(
                        expected.averagePositionRatio()
                ) != 0
                || maxPositionRatio.compareTo(expected.maxPositionRatio()) != 0) {
            throw new IllegalArgumentException(
                    "exposureSummary must match equityCurve."
            );
        }
    }

    private static void validateRate(String name, BigDecimal value) {
        Objects.requireNonNull(value, name + " must not be null.");
        if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1.");
        }
    }
}
