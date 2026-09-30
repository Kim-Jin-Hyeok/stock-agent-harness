package com.stock.backtest.performance.benchmark;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Component
public class BacktestBenchmarkPerformanceCalculator {
    private static final MathContext RATE_MATH_CONTEXT =
            MathContext.DECIMAL128;

    public BacktestBenchmarkPerformanceSummary calculate(
            BacktestBenchmarkSeries series,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        Objects.requireNonNull(series, "series must not be null.");
        Objects.requireNonNull(fromDate, "fromDate must not be null.");
        Objects.requireNonNull(toDate, "toDate must not be null.");
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                    "fromDate must not be after toDate."
            );
        }

        BacktestBenchmarkObservation startObservation =
                findRequiredObservation(series, fromDate, "fromDate");
        BacktestBenchmarkObservation endObservation =
                findRequiredObservation(series, toDate, "toDate");
        List<BacktestBenchmarkObservation> rangeObservations = series
                .observations()
                .stream()
                .filter(observation -> !observation.observationDate()
                        .isBefore(fromDate))
                .filter(observation -> !observation.observationDate()
                        .isAfter(toDate))
                .toList();

        Drawdown drawdown = calculateMaxDrawdown(rangeObservations);
        return new BacktestBenchmarkPerformanceSummary(
                series.benchmarkId(),
                fromDate,
                toDate,
                startObservation.closeValue(),
                endObservation.closeValue(),
                returnRate(
                        startObservation.closeValue(),
                        endObservation.closeValue()
                ),
                drawdown.rate(),
                drawdown.peakDate(),
                drawdown.troughDate(),
                rangeObservations.size()
        );
    }

    private BacktestBenchmarkObservation findRequiredObservation(
            BacktestBenchmarkSeries series,
            LocalDate requiredDate,
            String boundaryName
    ) {
        return series.observations()
                .stream()
                .filter(observation -> observation.observationDate()
                        .equals(requiredDate))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Benchmark observation not found for "
                                + boundaryName + ": " + requiredDate
                ));
    }

    private Drawdown calculateMaxDrawdown(
            List<BacktestBenchmarkObservation> observations
    ) {
        BacktestBenchmarkObservation firstObservation =
                observations.getFirst();
        BigDecimal runningPeakValue = firstObservation.closeValue();
        LocalDate runningPeakDate = firstObservation.observationDate();
        BigDecimal maxDrawdownRate = BigDecimal.ZERO;
        LocalDate maxDrawdownPeakDate = null;
        LocalDate maxDrawdownTroughDate = null;

        for (BacktestBenchmarkObservation observation : observations) {
            if (observation.closeValue().compareTo(runningPeakValue) > 0) {
                runningPeakValue = observation.closeValue();
                runningPeakDate = observation.observationDate();
            }
            BigDecimal drawdownRate = runningPeakValue
                    .subtract(observation.closeValue())
                    .divide(runningPeakValue, RATE_MATH_CONTEXT);
            if (drawdownRate.compareTo(maxDrawdownRate) > 0) {
                maxDrawdownRate = drawdownRate;
                maxDrawdownPeakDate = runningPeakDate;
                maxDrawdownTroughDate = observation.observationDate();
            }
        }
        return new Drawdown(
                maxDrawdownRate,
                maxDrawdownPeakDate,
                maxDrawdownTroughDate
        );
    }

    private BigDecimal returnRate(
            BigDecimal startValue,
            BigDecimal endValue
    ) {
        return endValue
                .divide(startValue, RATE_MATH_CONTEXT)
                .subtract(BigDecimal.ONE);
    }

    private record Drawdown(
            BigDecimal rate,
            LocalDate peakDate,
            LocalDate troughDate
    ) {
    }
}
