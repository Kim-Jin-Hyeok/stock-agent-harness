package com.stock.backtest.performance.exposure;

import com.stock.backtest.performance.equity.BacktestEquitySnapshot;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class BacktestExposureCalculator {
    private static final MathContext RATE_MATH_CONTEXT = MathContext.DECIMAL128;

    private BacktestExposureCalculator() {
    }

    public static BacktestExposureSummary calculate(
            List<BacktestEquitySnapshot> equityCurve
    ) {
        equityCurve = List.copyOf(Objects.requireNonNull(
                equityCurve,
                "equityCurve must not be null."
        ));
        if (equityCurve.isEmpty()) {
            throw new IllegalArgumentException("equityCurve must not be empty.");
        }

        LocalDate previousDate = null;
        int investedCount = 0;
        BigDecimal positionRatioSum = BigDecimal.ZERO;
        BigDecimal maxPositionRatio = BigDecimal.ZERO;
        for (BacktestEquitySnapshot snapshot : equityCurve) {
            if (previousDate != null
                    && !snapshot.valuationDate().isAfter(previousDate)) {
                throw new IllegalArgumentException(
                        "equityCurve must be ordered by unique valuationDate."
                );
            }
            previousDate = snapshot.valuationDate();
            if (snapshot.totalAssetAmountKrw() <= 0L) {
                throw new IllegalArgumentException(
                        "Exposure calculation requires positive total assets "
                                + "at every valuation."
                );
            }
            if (snapshot.positionEvaluationAmountKrw() > 0L) {
                investedCount++;
            }
            BigDecimal positionRatio = BigDecimal.valueOf(
                    snapshot.positionEvaluationAmountKrw()
            ).divide(
                    BigDecimal.valueOf(snapshot.totalAssetAmountKrw()),
                    RATE_MATH_CONTEXT
            );
            positionRatioSum = positionRatioSum.add(positionRatio);
            maxPositionRatio = maxPositionRatio.max(positionRatio);
        }

        BigDecimal observationCount = BigDecimal.valueOf(equityCurve.size());
        return new BacktestExposureSummary(
                equityCurve.size(),
                investedCount,
                BigDecimal.valueOf(investedCount).divide(
                        observationCount,
                        RATE_MATH_CONTEXT
                ),
                positionRatioSum.divide(observationCount, RATE_MATH_CONTEXT),
                maxPositionRatio
        );
    }
}
