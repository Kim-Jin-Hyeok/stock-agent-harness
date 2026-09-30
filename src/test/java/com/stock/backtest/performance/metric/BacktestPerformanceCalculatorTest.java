package com.stock.backtest.performance.metric;

import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.BacktestPosition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BacktestPerformanceCalculatorTest {
    private static final LocalDate FIRST_DATE =
            LocalDate.of(2026, 9, 1);
    private static final long INITIAL_EQUITY = 1_000_000L;

    private final BacktestPerformanceCalculator calculator =
            new BacktestPerformanceCalculator();

    @Test
    void calculatesPositiveTotalReturn() {
        List<BacktestEquitySnapshot> equityCurve = List.of(
                snapshot(0, 1_050_000L),
                snapshot(1, 1_100_000L)
        );

        BacktestPerformanceSummary summary = calculator.calculate(
                INITIAL_EQUITY,
                equityCurve
        );

        assertThat(summary.initialEquityAmountKrw())
                .isEqualTo(INITIAL_EQUITY);
        assertThat(summary.finalEquityAmountKrw())
                .isEqualTo(1_100_000L);
        assertThat(summary.netProfitAmountKrw()).isEqualTo(100_000L);
        assertThat(summary.totalReturnRate())
                .isEqualByComparingTo(new BigDecimal("0.1"));
        assertThat(summary.observationCount()).isEqualTo(2);
    }

    @Test
    void calculatesFromCashOnlyInitialPortfolio() {
        BacktestPerformanceSummary summary = calculator.calculate(
                BacktestPortfolioState.withCash(INITIAL_EQUITY),
                List.of(snapshot(0, 1_100_000L))
        );

        assertThat(summary.totalReturnRate())
                .isEqualByComparingTo(new BigDecimal("0.1"));
    }

    @Test
    void calculatesNegativeTotalReturn() {
        BacktestPerformanceSummary summary = calculator.calculate(
                INITIAL_EQUITY,
                List.of(
                        snapshot(0, 900_000L),
                        snapshot(1, 950_000L)
                )
        );

        assertThat(summary.netProfitAmountKrw()).isEqualTo(-50_000L);
        assertThat(summary.totalReturnRate())
                .isEqualByComparingTo(new BigDecimal("-0.05"));
    }

    @Test
    void returnsZeroDrawdownForMonotonicGrowth() {
        BacktestPerformanceSummary summary = calculator.calculate(
                INITIAL_EQUITY,
                List.of(
                        snapshot(0, 1_000_000L),
                        snapshot(1, 1_050_000L),
                        snapshot(2, 1_100_000L)
                )
        );

        assertThat(summary.maxDrawdownAmountKrw()).isZero();
        assertThat(summary.maxDrawdownRate()).isEqualByComparingTo(
                BigDecimal.ZERO
        );
        assertThat(summary.maxDrawdownPeakDate()).isNull();
        assertThat(summary.maxDrawdownTroughDate()).isNull();
    }

    @Test
    void calculatesMaximumDrawdownFromPeakToTrough() {
        BacktestPerformanceSummary summary = calculator.calculate(
                100L,
                List.of(
                        snapshot(0, 110L),
                        snapshot(1, 120L),
                        snapshot(2, 90L),
                        snapshot(3, 100L)
                )
        );

        assertThat(summary.maxDrawdownAmountKrw()).isEqualTo(30L);
        assertThat(summary.maxDrawdownRate())
                .isEqualByComparingTo(new BigDecimal("0.25"));
        assertThat(summary.maxDrawdownPeakDate())
                .isEqualTo(FIRST_DATE.plusDays(1));
        assertThat(summary.maxDrawdownTroughDate())
                .isEqualTo(FIRST_DATE.plusDays(2));
    }

    @Test
    void keepsHistoricalMddAfterEquityRecovery() {
        BacktestPerformanceSummary summary = calculator.calculate(
                100L,
                List.of(
                        snapshot(0, 110L),
                        snapshot(1, 120L),
                        snapshot(2, 90L),
                        snapshot(3, 130L)
                )
        );

        assertThat(summary.finalEquityAmountKrw()).isEqualTo(130L);
        assertThat(summary.maxDrawdownAmountKrw()).isEqualTo(30L);
        assertThat(summary.maxDrawdownRate())
                .isEqualByComparingTo(new BigDecimal("0.25"));
    }

    @Test
    void includesFirstTradeCostLossAgainstInitialEquity() {
        BacktestPerformanceSummary summary = calculator.calculate(
                INITIAL_EQUITY,
                List.of(snapshot(0, 999_119L))
        );

        assertThat(summary.netProfitAmountKrw()).isEqualTo(-881L);
        assertThat(summary.totalReturnRate())
                .isEqualByComparingTo(new BigDecimal("-0.000881"));
        assertThat(summary.maxDrawdownAmountKrw()).isEqualTo(881L);
        assertThat(summary.maxDrawdownRate())
                .isEqualByComparingTo(new BigDecimal("0.000881"));
        assertThat(summary.maxDrawdownPeakDate()).isEqualTo(FIRST_DATE);
        assertThat(summary.maxDrawdownTroughDate()).isEqualTo(FIRST_DATE);
    }

    @Test
    void rejectsEmptyEquityCurve() {
        assertThatThrownBy(() -> calculator.calculate(
                INITIAL_EQUITY,
                List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("equityCurve must not be empty.");
    }

    @Test
    void rejectsNonPositiveInitialEquity() {
        assertThatThrownBy(() -> calculator.calculate(
                0L,
                List.of(snapshot(0, 1_000_000L))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "initialEquityAmountKrw must be positive."
                );
    }

    @Test
    void rejectsInitialPortfolioWithPositions() {
        BacktestPortfolioState initialPortfolioState =
                new BacktestPortfolioState(
                        500_000L,
                        List.of(new BacktestPosition(
                                "005930",
                                5L,
                                100_000L
                        ))
                );

        assertThatThrownBy(() -> calculator.calculate(
                initialPortfolioState,
                List.of(snapshot(0, 1_000_000L))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "initialPortfolioState must not contain positions "
                                + "for cash-start performance calculation."
                );
    }

    @Test
    void rejectsEquityCurveOutsideValuationDateOrder() {
        assertThatThrownBy(() -> calculator.calculate(
                INITIAL_EQUITY,
                List.of(
                        snapshot(1, 1_000_000L),
                        snapshot(0, 1_010_000L)
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "equityCurve must be ordered by unique "
                                + "valuationDate."
                );
    }

    private BacktestEquitySnapshot snapshot(
            int dayOffset,
            long totalAssetAmountKrw
    ) {
        LocalDate valuationDate = FIRST_DATE.plusDays(dayOffset);
        return new BacktestEquitySnapshot(
                valuationDate,
                valuationDate.atTime(9, 10)
                        .atZone(ZoneId.of("Asia/Seoul"))
                        .toInstant(),
                totalAssetAmountKrw,
                0L,
                totalAssetAmountKrw
        );
    }
}
