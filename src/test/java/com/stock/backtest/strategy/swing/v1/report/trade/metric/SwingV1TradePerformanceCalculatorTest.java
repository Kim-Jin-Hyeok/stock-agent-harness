package com.stock.backtest.strategy.swing.v1.report.trade.metric;

import com.stock.agent.InvestmentAction;
import com.stock.backtest.execution.fill.BacktestFillType;
import com.stock.backtest.execution.fill.daily.DailyOpenFillApproximation;
import com.stock.backtest.strategy.swing.v1.report.trade.SwingV1CompletedTrade;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        assertThat(summary.largestWinningTradeNetProfitAmountKrw())
                .isEqualTo(2_000L);
        assertThat(summary.largestWinningTradeProfitShare())
                .isEqualByComparingTo(BigDecimal.ONE);
        assertThat(summary.netProfitLossExcludingLargestWinningTradeAmountKrw())
                .isEqualTo(-1_000L);
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
        assertThat(summary.largestWinningTradeNetProfitAmountKrw()).isZero();
        assertThat(summary.largestWinningTradeProfitShare()).isNull();
        assertThat(summary.netProfitLossExcludingLargestWinningTradeAmountKrw())
                .isZero();
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
        assertThat(summary.largestWinningTradeNetProfitAmountKrw())
                .isEqualTo(2_000L);
        assertThat(summary.largestWinningTradeProfitShare())
                .isEqualByComparingTo(BigDecimal.valueOf(2L).divide(
                        BigDecimal.valueOf(3L), MathContext.DECIMAL128
                ));
        assertThat(summary.netProfitLossExcludingLargestWinningTradeAmountKrw())
                .isEqualTo(1_000L);
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
        assertThat(summary.largestWinningTradeNetProfitAmountKrw()).isZero();
        assertThat(summary.largestWinningTradeProfitShare()).isNull();
        assertThat(summary.netProfitLossExcludingLargestWinningTradeAmountKrw())
                .isEqualTo(-1_000L);
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
        assertThat(summary.largestWinningTradeNetProfitAmountKrw()).isZero();
        assertThat(summary.largestWinningTradeProfitShare()).isNull();
        assertThat(summary.netProfitLossExcludingLargestWinningTradeAmountKrw())
                .isZero();
    }

    @Test
    void calculatesLargestProfitShareAgainstWinningTotalNotNetProfit() {
        SwingV1TradePerformanceSummary summary = calculator.calculate(List.of(
                trade(10_000L, 11_000L),
                trade(10_000L, 12_000L),
                trade(10_000L, 8_500L)
        ));

        assertThat(summary.totalNetProfitLossAmountKrw()).isEqualTo(1_500L);
        assertThat(summary.largestWinningTradeNetProfitAmountKrw())
                .isEqualTo(2_000L);
        assertThat(summary.largestWinningTradeProfitShare())
                .isEqualByComparingTo(BigDecimal.valueOf(2L).divide(
                        BigDecimal.valueOf(3L), MathContext.DECIMAL128
                ));
        assertThat(summary.netProfitLossExcludingLargestWinningTradeAmountKrw())
                .isEqualTo(-500L);
    }

    @Test
    void excludesOnlyOneLargestWinnerWhenProfitsAreTied() {
        List<SwingV1CompletedTrade> trades = List.of(
                trade(10_000L, 12_000L),
                trade(10_000L, 12_000L),
                trade(10_000L, 11_000L),
                trade(10_000L, 9_000L)
        );

        SwingV1TradePerformanceSummary summary = calculator.calculate(trades);

        assertThat(summary.largestWinningTradeNetProfitAmountKrw())
                .isEqualTo(2_000L);
        assertThat(summary.largestWinningTradeProfitShare())
                .isEqualByComparingTo(new BigDecimal("0.4"));
        assertThat(summary.netProfitLossExcludingLargestWinningTradeAmountKrw())
                .isEqualTo(2_000L);
        assertThat(calculator.calculate(trades.reversed())).isEqualTo(summary);
    }

    @ParameterizedTest
    @ValueSource(longs = {7_000L, 9_000L})
    void calculatesConcentrationEvenWhenTotalNetProfitIsNonPositive(
            long losingExitPrice
    ) {
        SwingV1TradePerformanceSummary summary = calculator.calculate(List.of(
                trade(10_000L, 11_000L),
                trade(10_000L, losingExitPrice)
        ));

        assertThat(summary.totalNetProfitLossAmountKrw()).isLessThanOrEqualTo(0L);
        assertThat(summary.largestWinningTradeNetProfitAmountKrw())
                .isEqualTo(1_000L);
        assertThat(summary.largestWinningTradeProfitShare())
                .isEqualByComparingTo(BigDecimal.ONE);
        assertThat(summary.netProfitLossExcludingLargestWinningTradeAmountKrw())
                .isEqualTo(losingExitPrice - 10_000L);
    }

    @Test
    void calculatesLargestProfitAndShareAfterDeductingCosts() {
        TradeCostModel commissionModel = commissionModel();
        SwingV1TradePerformanceSummary summary = calculator.calculate(List.of(
                trade(10_000L, 11_000L, commissionModel),
                trade(10_000L, 12_000L, commissionModel)
        ));

        assertThat(summary.totalWinningNetProfitAmountKrw()).isEqualTo(2_570L);
        assertThat(summary.largestWinningTradeNetProfitAmountKrw())
                .isEqualTo(1_780L);
        assertThat(summary.largestWinningTradeProfitShare())
                .isEqualByComparingTo(BigDecimal.valueOf(1_780L).divide(
                        BigDecimal.valueOf(2_570L), MathContext.DECIMAL128
                ));
        assertThat(summary.netProfitLossExcludingLargestWinningTradeAmountKrw())
                .isEqualTo(790L);
    }

    @Test
    void doesNotTreatGrossProfitAsWinningProfitAfterCosts() {
        SwingV1CompletedTrade trade = trade(10_000L, 10_100L, commissionModel());

        SwingV1TradePerformanceSummary summary = calculator.calculate(List.of(trade));

        assertThat(trade.grossProfitLossAmountKrw()).isEqualTo(100L);
        assertThat(trade.netProfitLossAmountKrw()).isEqualTo(-101L);
        assertThat(summary.winningTradeCount()).isZero();
        assertThat(summary.largestWinningTradeNetProfitAmountKrw()).isZero();
        assertThat(summary.largestWinningTradeProfitShare()).isNull();
        assertThat(summary.netProfitLossExcludingLargestWinningTradeAmountKrw())
                .isEqualTo(-101L);
    }

    private SwingV1CompletedTrade trade(
            long entryReferencePriceKrw,
            long exitReferencePriceKrw
    ) {
        return trade(entryReferencePriceKrw, exitReferencePriceKrw, zeroCostModel());
    }

    private SwingV1CompletedTrade trade(
            long entryReferencePriceKrw,
            long exitReferencePriceKrw,
            TradeCostModel costModel
    ) {
        return SwingV1CompletedTrade.from(
                fill(
                        InvestmentAction.BUY,
                        ENTRY_SIGNAL_DATE,
                        ENTRY_FILL_DATE,
                        entryReferencePriceKrw,
                        costModel
                ),
                fill(
                        InvestmentAction.SELL,
                        EXIT_SIGNAL_DATE,
                        EXIT_FILL_DATE,
                        exitReferencePriceKrw,
                        costModel
                )
        );
    }

    private DailyOpenFillApproximation fill(
            InvestmentAction action,
            LocalDate signalDate,
            LocalDate fillDate,
            long referencePriceKrw,
            TradeCostModel costModel
    ) {
        return new DailyOpenFillApproximation(
                BacktestFillType.DAILY_OPEN_FILL_APPROXIMATION,
                SYMBOL,
                signalDate,
                fillDate,
                new TradeCostCalculator().calculate(
                        costModel,
                        action,
                        1L,
                        referencePriceKrw
                )
        );
    }

    private TradeCostModel commissionModel() {
        return new TradeCostModel(
                "BACKTEST_COMMISSION", 1,
                new BigDecimal("0.01"), new BigDecimal("0.01"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
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
