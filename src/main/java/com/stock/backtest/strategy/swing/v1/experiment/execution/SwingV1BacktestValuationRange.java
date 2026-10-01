package com.stock.backtest.strategy.swing.v1.experiment.execution;

import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReport;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

record SwingV1BacktestValuationRange(
        LocalDate fromDate,
        LocalDate toDate
) {
    SwingV1BacktestValuationRange {
        Objects.requireNonNull(fromDate, "fromDate must not be null.");
        Objects.requireNonNull(toDate, "toDate must not be null.");
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                    "fromDate must not be after toDate."
            );
        }
    }

    static SwingV1BacktestValuationRange fromReports(
            List<SwingV1BacktestReport> reports
    ) {
        Objects.requireNonNull(reports, "reports must not be null.");
        if (reports.isEmpty()) {
            throw new IllegalArgumentException(
                    "reports must not be empty."
            );
        }

        SwingV1BacktestValuationRange expected = fromReport(
                reports.getFirst()
        );
        for (int index = 1; index < reports.size(); index++) {
            if (!expected.equals(fromReport(reports.get(index)))) {
                throw new IllegalArgumentException(
                        "Report valuation date ranges must match "
                                + "across symbols."
                );
            }
        }
        return expected;
    }

    private static SwingV1BacktestValuationRange fromReport(
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
        return new SwingV1BacktestValuationRange(
                equityCurve.getFirst().valuationDate(),
                equityCurve.getLast().valuationDate()
        );
    }
}
