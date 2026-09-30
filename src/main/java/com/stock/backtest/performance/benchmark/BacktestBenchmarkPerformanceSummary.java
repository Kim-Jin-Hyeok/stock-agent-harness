package com.stock.backtest.performance.benchmark;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.Objects;

public record BacktestBenchmarkPerformanceSummary(
        String benchmarkId,
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal startValue,
        BigDecimal endValue,
        BigDecimal totalReturnRate,
        BigDecimal maxDrawdownRate,
        LocalDate maxDrawdownPeakDate,
        LocalDate maxDrawdownTroughDate,
        int observationCount
) {
    private static final MathContext RATE_MATH_CONTEXT =
            MathContext.DECIMAL128;

    public BacktestBenchmarkPerformanceSummary {
        if (benchmarkId == null || benchmarkId.isBlank()) {
            throw new IllegalArgumentException(
                    "benchmarkId must not be blank."
            );
        }
        Objects.requireNonNull(fromDate, "fromDate must not be null.");
        Objects.requireNonNull(toDate, "toDate must not be null.");
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                    "fromDate must not be after toDate."
            );
        }
        validatePositiveValue("startValue", startValue);
        validatePositiveValue("endValue", endValue);
        validateTotalReturnRate(startValue, endValue, totalReturnRate);
        validateDrawdown(
                fromDate,
                toDate,
                maxDrawdownRate,
                maxDrawdownPeakDate,
                maxDrawdownTroughDate
        );
        if (observationCount < 1) {
            throw new IllegalArgumentException(
                    "observationCount must be at least 1."
            );
        }
        if (fromDate.equals(toDate) && observationCount != 1) {
            throw new IllegalArgumentException(
                    "A single-date summary must contain one observation."
            );
        }
        if (!fromDate.equals(toDate) && observationCount < 2) {
            throw new IllegalArgumentException(
                    "A multi-date summary must contain at least two "
                            + "observations."
            );
        }
    }

    private static void validatePositiveValue(
            String fieldName,
            BigDecimal value
    ) {
        Objects.requireNonNull(value, fieldName + " must not be null.");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive."
            );
        }
    }

    private static void validateTotalReturnRate(
            BigDecimal startValue,
            BigDecimal endValue,
            BigDecimal totalReturnRate
    ) {
        Objects.requireNonNull(
                totalReturnRate,
                "totalReturnRate must not be null."
        );
        BigDecimal expectedReturnRate = endValue
                .divide(startValue, RATE_MATH_CONTEXT)
                .subtract(BigDecimal.ONE);
        if (totalReturnRate.compareTo(expectedReturnRate) != 0) {
            throw new IllegalArgumentException(
                    "totalReturnRate must match startValue and endValue."
            );
        }
    }

    private static void validateDrawdown(
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal maxDrawdownRate,
            LocalDate maxDrawdownPeakDate,
            LocalDate maxDrawdownTroughDate
    ) {
        Objects.requireNonNull(
                maxDrawdownRate,
                "maxDrawdownRate must not be null."
        );
        if (maxDrawdownRate.signum() < 0
                || maxDrawdownRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(
                    "maxDrawdownRate must be at least 0 and less than 1."
            );
        }
        boolean hasDrawdown = maxDrawdownRate.signum() > 0;
        if (!hasDrawdown) {
            if (maxDrawdownPeakDate != null
                    || maxDrawdownTroughDate != null) {
                throw new IllegalArgumentException(
                        "Zero max drawdown must not contain dates."
                );
            }
            return;
        }

        Objects.requireNonNull(
                maxDrawdownPeakDate,
                "maxDrawdownPeakDate must not be null."
        );
        Objects.requireNonNull(
                maxDrawdownTroughDate,
                "maxDrawdownTroughDate must not be null."
        );
        if (maxDrawdownPeakDate.isBefore(fromDate)
                || maxDrawdownTroughDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                    "Max drawdown dates must belong to the summary range."
            );
        }
        if (maxDrawdownPeakDate.isAfter(maxDrawdownTroughDate)) {
            throw new IllegalArgumentException(
                    "maxDrawdownPeakDate must not be after "
                            + "maxDrawdownTroughDate."
            );
        }
    }
}
