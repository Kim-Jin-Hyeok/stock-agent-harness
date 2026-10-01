package com.stock.backtest.strategy.swing.v1.experiment.execution;

import com.stock.backtest.performance.benchmark.BacktestBenchmarkObservation;
import com.stock.backtest.performance.benchmark.BacktestBenchmarkSeries;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReport;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

record SwingV1BacktestValuationDates(
        List<LocalDate> dates
) {
    SwingV1BacktestValuationDates {
        dates = List.copyOf(Objects.requireNonNull(
                dates,
                "dates must not be null."
        ));
        if (dates.isEmpty()) {
            throw new IllegalArgumentException("dates must not be empty.");
        }
        LocalDate previousDate = null;
        for (LocalDate date : dates) {
            if (previousDate != null && !date.isAfter(previousDate)) {
                throw new IllegalArgumentException(
                        "dates must be ordered by unique valuation date."
                );
            }
            previousDate = date;
        }
    }

    LocalDate fromDate() {
        return dates.getFirst();
    }

    LocalDate toDate() {
        return dates.getLast();
    }

    static SwingV1BacktestValuationDates fromReports(
            List<SwingV1BacktestReport> reports
    ) {
        Objects.requireNonNull(reports, "reports must not be null.");
        if (reports.isEmpty()) {
            throw new IllegalArgumentException(
                    "reports must not be empty."
            );
        }

        SwingV1BacktestValuationDates expected = fromReport(
                reports.getFirst()
        );
        for (int index = 1; index < reports.size(); index++) {
            SwingV1BacktestReport report = reports.get(index);
            SwingV1BacktestValuationDates actual = fromReport(report);
            if (!expected.equals(actual)) {
                throw new IllegalArgumentException(
                        "Report valuation dates must match across symbols. "
                                + "symbol=" + report.request().candidateSymbol()
                                + ", " + mismatch(expected.dates(), actual.dates())
                );
            }
        }
        return expected;
    }

    void validateBenchmarkDates(BacktestBenchmarkSeries series) {
        Objects.requireNonNull(series, "series must not be null.");
        List<LocalDate> benchmarkDates = series.observations()
                .stream()
                .map(BacktestBenchmarkObservation::observationDate)
                .toList();
        if (!dates.equals(benchmarkDates)) {
            throw new IllegalStateException(
                    "Benchmark observation dates must match report "
                            + "valuation dates. benchmarkId="
                            + series.benchmarkId() + ", "
                            + mismatch(dates, benchmarkDates)
            );
        }
    }

    private static String mismatch(
            List<LocalDate> expected,
            List<LocalDate> actual
    ) {
        int index = 0;
        while (index < Math.min(expected.size(), actual.size())
                && expected.get(index).equals(actual.get(index))) {
            index++;
        }
        LocalDate expectedDate = index < expected.size()
                ? expected.get(index) : null;
        LocalDate actualDate = index < actual.size()
                ? actual.get(index) : null;
        return "expectedDate=" + expectedDate + ", actualDate=" + actualDate;
    }

    private static SwingV1BacktestValuationDates fromReport(
            SwingV1BacktestReport report
    ) {
        Objects.requireNonNull(report, "report must not be null.");
        Objects.requireNonNull(
                report.runResult(),
                "Report runResult must not be null."
        );
        List<BacktestEquitySnapshot> equityCurve = Objects.requireNonNull(
                report.runResult().equityCurve(),
                "Report equityCurve must not be null."
        );
        if (equityCurve.isEmpty()) {
            throw new IllegalArgumentException(
                    "Report equityCurve must not be empty."
            );
        }
        return new SwingV1BacktestValuationDates(
                equityCurve.stream()
                        .map(BacktestEquitySnapshot::valuationDate)
                        .toList()
        );
    }
}
