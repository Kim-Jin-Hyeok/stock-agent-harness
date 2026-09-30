package com.stock.backtest.strategy.swing.v1.experiment.summary;

import com.stock.backtest.performance.benchmark.BacktestBenchmarkPerformanceSummary;
import com.stock.backtest.performance.metric.BacktestPerformanceSummary;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunRequest;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentRequest;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentResult;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReport;
import com.stock.backtest.strategy.swing.v1.report.terminal.SwingV1TerminalLiquidationEstimate;
import com.stock.backtest.strategy.swing.v1.report.trade.metric.SwingV1TradePerformanceSummary;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwingV1BacktestExperimentSummaryCalculatorTest {
    private static final String BENCHMARK_ID = "KOSPI";
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );
    private static final LocalDate FROM_SIGNAL_DATE =
            LocalDate.of(2026, 1, 2);
    private static final LocalDate TO_SIGNAL_DATE =
            LocalDate.of(2026, 6, 30);
    private static final long INITIAL_CASH_AMOUNT_KRW = 10_000_000L;

    private final SwingV1BacktestExperimentSummaryCalculator calculator =
            new SwingV1BacktestExperimentSummaryCalculator();

    @Test
    void summarizesOutcomesUsingLiquidationAdjustedReturns() {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestExperimentResult result = result(
                request,
                List.of(
                        report(
                                request,
                                "005930",
                                new BigDecimal("-0.5"),
                                new BigDecimal("0.1"),
                                new BigDecimal("0.1"),
                                2
                        ),
                        report(
                                request,
                                "000660",
                                new BigDecimal("0.5"),
                                new BigDecimal("-0.2"),
                                new BigDecimal("0.3"),
                                0
                        ),
                        report(
                                request,
                                "035420",
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                new BigDecimal("0.05"),
                                1
                        )
                )
        );

        SwingV1BacktestExperimentSummary summary =
                calculator.calculate(result);

        assertThat(summary.request()).isSameAs(request);
        assertThat(summary.symbolCount()).isEqualTo(3);
        assertThat(summary.profitableSymbolCount()).isEqualTo(1);
        assertThat(summary.losingSymbolCount()).isEqualTo(1);
        assertThat(summary.breakEvenSymbolCount()).isEqualTo(1);
        assertThat(summary.noCompletedTradeSymbolCount()).isEqualTo(1);
        assertThat(summary.totalCompletedTradeCount()).isEqualTo(3);
        assertThat(summary.medianLiquidationAdjustedReturnRate())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.worstReturnSymbol()).isEqualTo("000660");
        assertThat(summary.worstLiquidationAdjustedReturnRate())
                .isEqualByComparingTo(new BigDecimal("-0.2"));
        assertThat(summary.worstDrawdownSymbol()).isEqualTo("000660");
        assertThat(summary.worstMaxDrawdownRate())
                .isEqualByComparingTo(new BigDecimal("0.3"));
    }

    @Test
    void calculatesEvenMedianAndResolvesTiesBySymbol() {
        SwingV1BacktestExperimentRequest request = request();
        SwingV1BacktestExperimentResult result = result(
                request,
                List.of(
                        report(
                                request,
                                "035420",
                                new BigDecimal("0.3"),
                                new BigDecimal("0.3"),
                                new BigDecimal("0.1"),
                                1
                        ),
                        report(
                                request,
                                "005930",
                                new BigDecimal("-0.2"),
                                new BigDecimal("-0.2"),
                                new BigDecimal("0.4"),
                                1
                        ),
                        report(
                                request,
                                "051910",
                                new BigDecimal("0.1"),
                                new BigDecimal("0.1"),
                                new BigDecimal("0.2"),
                                1
                        ),
                        report(
                                request,
                                "000660",
                                new BigDecimal("-0.2"),
                                new BigDecimal("-0.2"),
                                new BigDecimal("0.4"),
                                1
                        )
                )
        );

        SwingV1BacktestExperimentSummary summary =
                calculator.calculate(result);

        assertThat(summary.medianLiquidationAdjustedReturnRate())
                .isEqualByComparingTo(new BigDecimal("-0.05"));
        assertThat(summary.worstReturnSymbol()).isEqualTo("000660");
        assertThat(summary.worstDrawdownSymbol()).isEqualTo("000660");
    }

    @Test
    void rejectsNullExperimentResult() {
        assertThatNullPointerException()
                .isThrownBy(() -> calculator.calculate(null))
                .withMessage("experimentResult must not be null.");
    }

    private SwingV1BacktestExperimentResult result(
            SwingV1BacktestExperimentRequest request,
            List<SwingV1BacktestReport> reports
    ) {
        return new SwingV1BacktestExperimentResult(
                request,
                reports,
                benchmarkSummary(request)
        );
    }

    private BacktestBenchmarkPerformanceSummary benchmarkSummary(
            SwingV1BacktestExperimentRequest request
    ) {
        return new BacktestBenchmarkPerformanceSummary(
                request.benchmarkId(),
                request.fromSignalDate(),
                request.toSignalDate(),
                new BigDecimal("100"),
                new BigDecimal("110"),
                new BigDecimal("0.1"),
                BigDecimal.ZERO,
                null,
                null,
                2
        );
    }

    private SwingV1BacktestReport report(
            SwingV1BacktestExperimentRequest request,
            String symbol,
            BigDecimal markToMarketReturnRate,
            BigDecimal liquidationAdjustedReturnRate,
            BigDecimal maxDrawdownRate,
            int completedTradeCount
    ) {
        SwingV1BacktestReport report = mock(SwingV1BacktestReport.class);
        SwingV1TerminalLiquidationEstimate terminalEstimate = mock(
                SwingV1TerminalLiquidationEstimate.class
        );
        BacktestPerformanceSummary performanceSummary = mock(
                BacktestPerformanceSummary.class
        );
        SwingV1TradePerformanceSummary tradePerformanceSummary = mock(
                SwingV1TradePerformanceSummary.class
        );
        when(report.request()).thenReturn(runRequest(request, symbol));
        when(terminalEstimate.liquidationAdjustedTotalReturnRate())
                .thenReturn(liquidationAdjustedReturnRate);
        when(report.terminalLiquidationEstimate())
                .thenReturn(terminalEstimate);
        when(performanceSummary.totalReturnRate())
                .thenReturn(markToMarketReturnRate);
        when(performanceSummary.maxDrawdownRate())
                .thenReturn(maxDrawdownRate);
        when(report.performanceSummary()).thenReturn(performanceSummary);
        when(tradePerformanceSummary.completedTradeCount())
                .thenReturn(completedTradeCount);
        when(report.tradePerformanceSummary())
                .thenReturn(tradePerformanceSummary);
        return report;
    }

    private SwingV1BacktestRunRequest runRequest(
            SwingV1BacktestExperimentRequest request,
            String symbol
    ) {
        return new SwingV1BacktestRunRequest(
                request.strategyIdentity(),
                symbol,
                request.fromSignalDate(),
                request.toSignalDate(),
                BacktestPortfolioState.withCash(
                        request.initialCashAmountKrwPerSymbol()
                ),
                request.costModel()
        );
    }

    private SwingV1BacktestExperimentRequest request() {
        return new SwingV1BacktestExperimentRequest(
                STRATEGY_IDENTITY,
                BENCHMARK_ID,
                FROM_SIGNAL_DATE,
                TO_SIGNAL_DATE,
                INITIAL_CASH_AMOUNT_KRW,
                new TradeCostModel(
                        "KIS_SIMULATION_V1",
                        1,
                        new BigDecimal("0.00015"),
                        new BigDecimal("0.00015"),
                        new BigDecimal("0.0018"),
                        new BigDecimal("0.0010"),
                        new BigDecimal("0.0010")
                )
        );
    }
}
