package com.stock.backtest.strategy.swing.v1.report.trade.metric;

import com.stock.agent.InvestmentAction;
import com.stock.backtest.execution.fill.BacktestFillType;
import com.stock.backtest.execution.fill.daily.DailyOpenFillApproximation;
import com.stock.backtest.strategy.swing.v1.report.trade.SwingV1CompletedTrade;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SwingV1TradePerformanceCalculatorTest {
    private static final String SYMBOL = "005930";
    private static final LocalDate ENTRY_SIGNAL_DATE =
            LocalDate.of(2026, 9, 24);
    private static final LocalDate ENTRY_FILL_DATE =
            LocalDate.of(2026, 9, 25);
    private static final LocalDate EXIT_SIGNAL_DATE =
            LocalDate.of(2026, 9, 28);
    private static final LocalDate EXIT_FILL_DATE =
            LocalDate.of(2026, 9, 29);

    private final SwingV1TradePerformanceCalculator calculator =
            new SwingV1TradePerformanceCalculator();

    @Test
    void calculatesMetricsFromWinLossAndBreakEvenTrades() {
        List<SwingV1CompletedTrade> completedTrades = List.of(
                trade(10_000L, 12_000L),
                trade(10_000L, 9_000L),
                trade(10_000L, 10_000L)
        );

        SwingV1TradePerformanceSummary summary = calculator.calculate(
                completedTrades
        );

        assertThat(summary.completedTradeCount()).isEqualTo(3);
        assertThat(summary.winningTradeCount()).isEqualTo(1);
        assertThat(summary.losingTradeCount()).isEqualTo(1);
        assertThat(summary.breakEvenTradeCount()).isEqualTo(1);
        assertThat(summary.totalNetProfitLossAmountKrw()).isEqualTo(1_000L);
        assertThat(summary.totalWinningNetProfitAmountKrw())
                .isEqualTo(2_000L);
        assertThat(summary.totalLosingNetLossAmountKrw())
                .isEqualTo(1_000L);
        assertThat(summary.winRate()).isEqualByComparingTo(
                BigDecimal.ONE.divide(
                        BigDecimal.valueOf(3L),
                        MathContext.DECIMAL128
                )
        );
        assertThat(summary.averageNetProfitLossAmountKrw())
                .isEqualByComparingTo(BigDecimal.valueOf(1_000L).divide(
                        BigDecimal.valueOf(3L),
                        MathContext.DECIMAL128
                ));
        assertThat(summary.averageWinningNetProfitAmountKrw())
                .isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(summary.averageLosingNetLossAmountKrw())
                .isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(summary.profitFactor().status())
                .isEqualTo(SwingV1ProfitFactorStatus.CALCULATED);
        assertThat(summary.profitFactor().value())
                .isEqualByComparingTo(new BigDecimal("2"));
    }

    @Test
    void reportsNoCompletedTradesWithoutRates() {
        SwingV1TradePerformanceSummary summary = calculator.calculate(
                List.of()
        );

        assertThat(summary.completedTradeCount()).isZero();
        assertThat(summary.winRate()).isNull();
        assertThat(summary.averageNetProfitLossAmountKrw()).isNull();
        assertThat(summary.profitFactor().status())
                .isEqualTo(
                        SwingV1ProfitFactorStatus.NO_COMPLETED_TRADES
                );
        assertThat(summary.profitFactor().value()).isNull();
    }

    @Test
    void reportsProfitWithoutLossInsteadOfInfiniteValue() {
        SwingV1TradePerformanceSummary summary = calculator.calculate(
                List.of(
                        trade(10_000L, 11_000L),
                        trade(10_000L, 12_000L)
                )
        );

        assertThat(summary.winRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(summary.profitFactor().status())
                .isEqualTo(SwingV1ProfitFactorStatus.PROFIT_WITHOUT_LOSS);
        assertThat(summary.profitFactor().value()).isNull();
    }

    @Test
    void calculatesZeroProfitFactorForLossOnlyTrades() {
        SwingV1TradePerformanceSummary summary = calculator.calculate(
                List.of(trade(10_000L, 9_000L))
        );

        assertThat(summary.profitFactor().status())
                .isEqualTo(SwingV1ProfitFactorStatus.CALCULATED);
        assertThat(summary.profitFactor().value())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void reportsNoProfitOrLossForBreakEvenTradesOnly() {
        SwingV1TradePerformanceSummary summary = calculator.calculate(
                List.of(trade(10_000L, 10_000L))
        );

        assertThat(summary.breakEvenTradeCount()).isEqualTo(1);
        assertThat(summary.averageNetProfitLossAmountKrw())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.profitFactor().status())
                .isEqualTo(SwingV1ProfitFactorStatus.NO_PROFIT_OR_LOSS);
        assertThat(summary.profitFactor().value()).isNull();
    }

    private SwingV1CompletedTrade trade(
            long entryReferencePriceKrw,
            long exitReferencePriceKrw
    ) {
        return SwingV1CompletedTrade.from(
                fill(
                        InvestmentAction.BUY,
                        ENTRY_SIGNAL_DATE,
                        ENTRY_FILL_DATE,
                        entryReferencePriceKrw
                ),
                fill(
                        InvestmentAction.SELL,
                        EXIT_SIGNAL_DATE,
                        EXIT_FILL_DATE,
                        exitReferencePriceKrw
                )
        );
    }

    private DailyOpenFillApproximation fill(
            InvestmentAction action,
            LocalDate signalDate,
            LocalDate fillDate,
            long referencePriceKrw
    ) {
        return new DailyOpenFillApproximation(
                BacktestFillType.DAILY_OPEN_FILL_APPROXIMATION,
                SYMBOL,
                signalDate,
                fillDate,
                new TradeCostCalculator().calculate(
                        zeroCostModel(),
                        action,
                        1L,
                        referencePriceKrw
                )
        );
    }

    private TradeCostModel zeroCostModel() {
        return new TradeCostModel(
                "BACKTEST_ZERO_COST",
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}
