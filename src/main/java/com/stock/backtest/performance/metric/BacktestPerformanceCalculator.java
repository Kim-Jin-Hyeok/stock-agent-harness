package com.stock.backtest.performance.metric;

import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Component
public class BacktestPerformanceCalculator {
    private static final MathContext RATE_MATH_CONTEXT =
            MathContext.DECIMAL128;

    public BacktestPerformanceSummary calculate(
            BacktestPortfolioState initialPortfolioState,
            List<BacktestEquitySnapshot> equityCurve
    ) {
        Objects.requireNonNull(
                initialPortfolioState,
                "initialPortfolioState must not be null."
        );
        if (!initialPortfolioState.positions().isEmpty()) {
            throw new IllegalArgumentException(
                    "initialPortfolioState must not contain positions for "
                            + "cash-start performance calculation."
            );
        }
        return calculate(
                initialPortfolioState.cashAmountKrw(),
                equityCurve
        );
    }

    public BacktestPerformanceSummary calculate(
            long initialEquityAmountKrw,
            List<BacktestEquitySnapshot> equityCurve
    ) {
        if (initialEquityAmountKrw <= 0) {
            throw new IllegalArgumentException(
                    "initialEquityAmountKrw must be positive."
            );
        }
        equityCurve = List.copyOf(Objects.requireNonNull(
                equityCurve,
                "equityCurve must not be null."
        ));
        if (equityCurve.isEmpty()) {
            throw new IllegalArgumentException(
                    "equityCurve must not be empty."
            );
        }
        validateValuationDates(equityCurve);

        BacktestEquitySnapshot firstSnapshot = equityCurve.getFirst();
        // The initial equity is the pre-trade peak on the first valuation day.
        long runningPeakAmountKrw = initialEquityAmountKrw;
        LocalDate runningPeakDate = firstSnapshot.valuationDate();
        long maxDrawdownAmountKrw = 0L;
        BigDecimal maxDrawdownRate = zeroRate();
        LocalDate maxDrawdownPeakDate = null;
        LocalDate maxDrawdownTroughDate = null;

        for (BacktestEquitySnapshot snapshot : equityCurve) {
            long currentEquityAmountKrw = snapshot.totalAssetAmountKrw();
            if (currentEquityAmountKrw > runningPeakAmountKrw) {
                runningPeakAmountKrw = currentEquityAmountKrw;
                runningPeakDate = snapshot.valuationDate();
            }

            long drawdownAmountKrw = Math.subtractExact(
                    runningPeakAmountKrw,
                    currentEquityAmountKrw
            );
            BigDecimal drawdownRate = rate(
                    drawdownAmountKrw,
                    runningPeakAmountKrw
            );
            if (drawdownRate.compareTo(maxDrawdownRate) > 0) {
                maxDrawdownAmountKrw = drawdownAmountKrw;
                maxDrawdownRate = drawdownRate;
                maxDrawdownPeakDate = runningPeakDate;
                maxDrawdownTroughDate = snapshot.valuationDate();
            }
        }

        long finalEquityAmountKrw = equityCurve
                .getLast()
                .totalAssetAmountKrw();
        long netProfitAmountKrw = Math.subtractExact(
                finalEquityAmountKrw,
                initialEquityAmountKrw
        );
        return new BacktestPerformanceSummary(
                initialEquityAmountKrw,
                finalEquityAmountKrw,
                netProfitAmountKrw,
                rate(netProfitAmountKrw, initialEquityAmountKrw),
                maxDrawdownAmountKrw,
                maxDrawdownRate,
                maxDrawdownPeakDate,
                maxDrawdownTroughDate,
                equityCurve.size()
        );
    }

    private void validateValuationDates(
            List<BacktestEquitySnapshot> equityCurve
    ) {
        LocalDate previousValuationDate = null;
        for (BacktestEquitySnapshot snapshot : equityCurve) {
            Objects.requireNonNull(
                    snapshot,
                    "equitySnapshot must not be null."
            );
            LocalDate valuationDate = snapshot.valuationDate();
            if (previousValuationDate != null
                    && !valuationDate.isAfter(previousValuationDate)) {
                throw new IllegalArgumentException(
                        "equityCurve must be ordered by unique "
                                + "valuationDate."
                );
            }
            previousValuationDate = valuationDate;
        }
    }

    private BigDecimal rate(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator).divide(
                BigDecimal.valueOf(denominator),
                RATE_MATH_CONTEXT
        );
    }

    private BigDecimal zeroRate() {
        return BigDecimal.ZERO;
    }
}
