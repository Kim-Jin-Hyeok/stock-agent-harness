package com.stock.backtest.strategy.swing.v1.report;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.performance.metric.BacktestPerformanceCalculator;
import com.stock.backtest.performance.metric.BacktestPerformanceSummary;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.BacktestPosition;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepResult;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunRequest;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunResult;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunService;
import com.stock.backtest.strategy.swing.v1.report.terminal.SwingV1TerminalLiquidationCalculator;
import com.stock.backtest.strategy.swing.v1.report.terminal.SwingV1TerminalLiquidationEstimate;
import com.stock.backtest.strategy.swing.v1.report.trade.SwingV1CompletedTradeExtractor;
import com.stock.backtest.strategy.swing.v1.report.trade.metric.SwingV1ProfitFactor;
import com.stock.backtest.strategy.swing.v1.report.trade.metric.SwingV1TradePerformanceCalculator;
import com.stock.backtest.strategy.swing.v1.report.trade.metric.SwingV1TradePerformanceSummary;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SwingV1BacktestReportServiceTest {
    private static final String SYMBOL = "005930";
    private static final LocalDate SIGNAL_DATE =
            LocalDate.of(2026, 9, 29);
    private static final LocalDate DECISION_DATE =
            LocalDate.of(2026, 9, 30);
    private static final long INITIAL_CASH_AMOUNT_KRW = 1_000_000L;
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );

    private final SwingV1BacktestRunService runService = mock(
            SwingV1BacktestRunService.class
    );
    private final SwingV1TerminalLiquidationCalculator
            terminalLiquidationCalculator = mock(
                    SwingV1TerminalLiquidationCalculator.class
            );
    private final SwingV1CompletedTradeExtractor completedTradeExtractor =
            mock(SwingV1CompletedTradeExtractor.class);
    private final SwingV1TradePerformanceCalculator
            tradePerformanceCalculator = mock(
                    SwingV1TradePerformanceCalculator.class
            );

    @Test
    void generatesReportFromRunResultAndEquityCurve() {
        SwingV1BacktestRunRequest request = request();
        SwingV1BacktestRunResult runResult = runResult(
                request.initialPortfolioState(),
                List.of(holdStep(request.initialPortfolioState()))
        );
        BacktestPerformanceCalculator performanceCalculator = mock(
                BacktestPerformanceCalculator.class
        );
        BacktestPerformanceSummary performanceSummary = summary(1);
        SwingV1TerminalLiquidationEstimate terminalEstimate =
                terminalEstimate();
        SwingV1TradePerformanceSummary tradePerformanceSummary =
                noTradePerformanceSummary();
        when(runService.execute(request)).thenReturn(runResult);
        when(performanceCalculator.calculate(
                runResult.initialPortfolioState(),
                runResult.equityCurve()
        )).thenReturn(performanceSummary);
        when(terminalLiquidationCalculator.calculate(
                performanceSummary.initialEquityAmountKrw(),
                runResult.finalPortfolioState(),
                runResult.equityCurve().getLast(),
                request.costModel()
        )).thenReturn(terminalEstimate);
        when(completedTradeExtractor.extract(runResult.steps()))
                .thenReturn(List.of());
        when(tradePerformanceCalculator.calculate(List.of()))
                .thenReturn(tradePerformanceSummary);
        SwingV1BacktestReportService service =
                new SwingV1BacktestReportService(
                        runService,
                        performanceCalculator,
                        terminalLiquidationCalculator,
                        completedTradeExtractor,
                        tradePerformanceCalculator
                );

        SwingV1BacktestReport report = service.generate(request);

        assertThat(report.request()).isSameAs(request);
        assertThat(report.runResult()).isSameAs(runResult);
        assertThat(report.performanceSummary())
                .isSameAs(performanceSummary);
        assertThat(report.terminalLiquidationEstimate())
                .isSameAs(terminalEstimate);
        assertThat(report.completedTrades()).isEmpty();
        assertThat(report.tradePerformanceSummary())
                .isSameAs(tradePerformanceSummary);
        assertThat(report.request().costModel())
                .isSameAs(request.costModel());
        verify(runService).execute(request);
        verify(performanceCalculator).calculate(
                runResult.initialPortfolioState(),
                runResult.equityCurve()
        );
        verify(terminalLiquidationCalculator).calculate(
                performanceSummary.initialEquityAmountKrw(),
                runResult.finalPortfolioState(),
                runResult.equityCurve().getLast(),
                request.costModel()
        );
        verify(completedTradeExtractor).extract(runResult.steps());
        verify(tradePerformanceCalculator).calculate(List.of());
    }

    @Test
    void rejectsRunWithoutEquitySnapshots() {
        SwingV1BacktestRunRequest request = request();
        SwingV1BacktestRunResult runResult = runResult(
                request.initialPortfolioState(),
                List.of()
        );
        when(runService.execute(request)).thenReturn(runResult);
        SwingV1BacktestReportService service =
                new SwingV1BacktestReportService(
                        runService,
                        new BacktestPerformanceCalculator(),
                        terminalLiquidationCalculator,
                        completedTradeExtractor,
                        tradePerformanceCalculator
                );

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("equityCurve must not be empty.");
    }

    @Test
    void rejectsInitialPositionBeforeBacktestRun() {
        SwingV1BacktestRunRequest request = new SwingV1BacktestRunRequest(
                STRATEGY_IDENTITY,
                SYMBOL,
                SIGNAL_DATE,
                SIGNAL_DATE,
                new BacktestPortfolioState(
                        500_000L,
                        List.of(new BacktestPosition(
                                SYMBOL,
                                5L,
                                100_000L
                        ))
                ),
                costModel()
        );
        BacktestPerformanceCalculator performanceCalculator = mock(
                BacktestPerformanceCalculator.class
        );
        SwingV1BacktestReportService service =
                new SwingV1BacktestReportService(
                        runService,
                        performanceCalculator,
                        terminalLiquidationCalculator,
                        completedTradeExtractor,
                        tradePerformanceCalculator
                );

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "initialPortfolioState must not contain positions "
                                + "for cash-start performance calculation."
                );
        verifyNoInteractions(
                runService,
                performanceCalculator,
                terminalLiquidationCalculator,
                completedTradeExtractor,
                tradePerformanceCalculator
        );
    }

    private SwingV1BacktestRunRequest request() {
        return new SwingV1BacktestRunRequest(
                STRATEGY_IDENTITY,
                SYMBOL,
                SIGNAL_DATE,
                SIGNAL_DATE,
                BacktestPortfolioState.withCash(
                        INITIAL_CASH_AMOUNT_KRW
                ),
                costModel()
        );
    }

    private SwingV1BacktestRunResult runResult(
            BacktestPortfolioState portfolioState,
            List<SwingV1BacktestStepResult> steps
    ) {
        return new SwingV1BacktestRunResult(
                STRATEGY_IDENTITY,
                SYMBOL,
                SIGNAL_DATE,
                SIGNAL_DATE,
                portfolioState,
                portfolioState,
                steps
        );
    }

    private SwingV1BacktestStepResult holdStep(
            BacktestPortfolioState portfolioState
    ) {
        return SwingV1BacktestStepResult.hold(
                SIGNAL_DATE,
                DECISION_DATE,
                new InvestmentDecision(
                        InvestmentAction.HOLD,
                        null,
                        null,
                        null,
                        "No signal."
                ),
                portfolioState,
                new BacktestEquitySnapshot(
                        DECISION_DATE,
                        DECISION_DATE.atTime(9, 10)
                                .atZone(ZoneId.of("Asia/Seoul"))
                                .toInstant(),
                        portfolioState.cashAmountKrw(),
                        0L,
                        portfolioState.cashAmountKrw()
                )
        );
    }

    private BacktestPerformanceSummary summary(int observationCount) {
        return new BacktestPerformanceSummary(
                INITIAL_CASH_AMOUNT_KRW,
                INITIAL_CASH_AMOUNT_KRW,
                0L,
                BigDecimal.ZERO,
                0L,
                BigDecimal.ZERO,
                null,
                null,
                observationCount
        );
    }

    private SwingV1TerminalLiquidationEstimate terminalEstimate() {
        return new SwingV1TerminalLiquidationEstimate(
                INITIAL_CASH_AMOUNT_KRW,
                INITIAL_CASH_AMOUNT_KRW,
                0L,
                INITIAL_CASH_AMOUNT_KRW,
                0L,
                BigDecimal.ZERO
        );
    }

    private SwingV1TradePerformanceSummary noTradePerformanceSummary() {
        return new SwingV1TradePerformanceSummary(
                0,
                0,
                0,
                0,
                0L,
                0L,
                0L,
                null,
                null,
                null,
                null,
                SwingV1ProfitFactor.noCompletedTrades()
        );
    }

    private TradeCostModel costModel() {
        return new TradeCostModel(
                "BACKTEST_COST_V1",
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}
