package com.stock.backtest.performance.metric;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record BacktestPerformanceSummary(
        long initialEquityAmountKrw,
        long finalEquityAmountKrw,
        long netProfitAmountKrw,
        BigDecimal totalReturnRate,
        long maxDrawdownAmountKrw,
        BigDecimal maxDrawdownRate,
        LocalDate maxDrawdownPeakDate,
        LocalDate maxDrawdownTroughDate,
        int observationCount
) {
    private static final BigDecimal MINIMUM_RETURN_RATE =
            BigDecimal.ONE.negate();

    public BacktestPerformanceSummary {
        if (initialEquityAmountKrw <= 0) {
            throw new IllegalArgumentException(
                    "initialEquityAmountKrw must be positive."
            );
        }
        if (finalEquityAmountKrw < 0) {
            throw new IllegalArgumentException(
                    "finalEquityAmountKrw must not be negative."
            );
        }
        if (netProfitAmountKrw != Math.subtractExact(
                finalEquityAmountKrw,
                initialEquityAmountKrw
        )) {
            throw new IllegalArgumentException(
                    "netProfitAmountKrw must match initial and final equity."
            );
        }
        Objects.requireNonNull(
                totalReturnRate,
                "totalReturnRate must not be null."
        );
        if (totalReturnRate.compareTo(MINIMUM_RETURN_RATE) < 0) {
            throw new IllegalArgumentException(
                    "totalReturnRate must not be less than -1."
            );
        }
        if (maxDrawdownAmountKrw < 0) {
            throw new IllegalArgumentException(
                    "maxDrawdownAmountKrw must not be negative."
            );
        }
        Objects.requireNonNull(
                maxDrawdownRate,
                "maxDrawdownRate must not be null."
        );
        if (maxDrawdownRate.signum() < 0
                || maxDrawdownRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "maxDrawdownRate must be between 0 and 1."
            );
        }
        validateDrawdownDates(
                maxDrawdownAmountKrw,
                maxDrawdownRate,
                maxDrawdownPeakDate,
                maxDrawdownTroughDate
        );
        if (observationCount < 1) {
            throw new IllegalArgumentException(
                    "observationCount must be at least 1."
            );
        }
    }

    private static void validateDrawdownDates(
            long maxDrawdownAmountKrw,
            BigDecimal maxDrawdownRate,
            LocalDate maxDrawdownPeakDate,
            LocalDate maxDrawdownTroughDate
    ) {
        boolean hasDrawdown = maxDrawdownAmountKrw > 0;
        if (hasDrawdown != (maxDrawdownRate.signum() > 0)) {
            throw new IllegalArgumentException(
                    "max drawdown amount and rate must both be zero or "
                            + "positive."
            );
        }
        if (!hasDrawdown) {
            if (maxDrawdownPeakDate != null
                    || maxDrawdownTroughDate != null) {
                throw new IllegalArgumentException(
                        "zero max drawdown must not contain dates."
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
        if (maxDrawdownPeakDate.isAfter(maxDrawdownTroughDate)) {
            throw new IllegalArgumentException(
                    "maxDrawdownPeakDate must not be after "
                            + "maxDrawdownTroughDate."
            );
        }
    }
}
