package com.stock.backtest.strategy.swing.v1.report;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.backtest.execution.fill.BacktestFillType;
import com.stock.backtest.execution.fill.daily.DailyOpenFillApproximation;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.performance.metric.BacktestPerformanceCalculator;
import com.stock.backtest.performance.metric.BacktestPerformanceSummary;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.transition.BacktestPortfolioTransitionService;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionResult;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepResult;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunRequest;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunResult;
import com.stock.backtest.strategy.swing.v1.report.terminal.SwingV1TerminalLiquidationEstimate;
import com.stock.backtest.strategy.swing.v1.report.trade.SwingV1CompletedTrade;
import com.stock.backtest.strategy.swing.v1.report.trade.SwingV1CompletedTradeExtractor;
import com.stock.backtest.strategy.swing.v1.report.trade.metric.SwingV1ProfitFactor;
import com.stock.backtest.strategy.swing.v1.report.trade.metric.SwingV1TradePerformanceCalculator;
import com.stock.backtest.strategy.swing.v1.report.trade.metric.SwingV1TradePerformanceSummary;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingV1BacktestReportTest {
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
    private final BacktestPortfolioState portfolioState =
            BacktestPortfolioState.withCash(INITIAL_CASH_AMOUNT_KRW);

    @Test
    void preservesRequestRunResultAndPerformanceSummary() {
        SwingV1BacktestRunRequest request = request(SYMBOL);
        SwingV1BacktestRunResult runResult = runResult(SYMBOL, true);
        BacktestPerformanceSummary performanceSummary = summary(1);

        SwingV1BacktestReport report = new SwingV1BacktestReport(
                request,
                runResult,
                performanceSummary,
                terminalEstimate(INITIAL_CASH_AMOUNT_KRW),
                List.of(),
                noTradePerformanceSummary()
        );

        assertThat(report.request()).isSameAs(request);
        assertThat(report.runResult()).isSameAs(runResult);
        assertThat(report.performanceSummary())
                .isSameAs(performanceSummary);
        assertThat(report.terminalLiquidationEstimate()
                .markToMarketFinalEquityAmountKrw())
                .isEqualTo(INITIAL_CASH_AMOUNT_KRW);
        assertThat(report.completedTrades()).isEmpty();
        assertThat(report.tradePerformanceSummary().completedTradeCount())
                .isZero();
    }

    @Test
    void rejectsRunResultForDifferentCandidateSymbol() {
        SwingV1BacktestRunRequest request = request(SYMBOL);
        SwingV1BacktestRunResult runResult = runResult("000660", true);

        assertThatThrownBy(() -> new SwingV1BacktestReport(
                request,
                runResult,
                summary(1),
                terminalEstimate(INITIAL_CASH_AMOUNT_KRW),
                List.of(),
                noTradePerformanceSummary()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "runResult candidateSymbol must match request."
                );
    }

    @Test
    void rejectsRunResultWithoutEquitySnapshots() {
        assertThatThrownBy(() -> new SwingV1BacktestReport(
                request(SYMBOL),
                runResult(SYMBOL, false),
                summary(1),
                terminalEstimate(INITIAL_CASH_AMOUNT_KRW),
                List.of(),
                noTradePerformanceSummary()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("runResult equityCurve must not be empty.");
    }

    @Test
    void rejectsPerformanceSummaryWithDifferentObservationCount() {
        assertThatThrownBy(() -> new SwingV1BacktestReport(
                request(SYMBOL),
                runResult(SYMBOL, true),
                summary(2),
                terminalEstimate(INITIAL_CASH_AMOUNT_KRW),
                List.of(),
                noTradePerformanceSummary()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "performanceSummary observation count must match "
                                + "runResult."
                );
    }

    @Test
    void rejectsTerminalEstimateWithDifferentMarkToMarketEquity() {
        assertThatThrownBy(() -> new SwingV1BacktestReport(
                request(SYMBOL),
                runResult(SYMBOL, true),
                summary(1),
                terminalEstimate(900_000L),
                List.of(),
                noTradePerformanceSummary()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "terminalLiquidationEstimate mark-to-market "
                                + "equity must match performanceSummary."
                );
    }

    @Test
    void rejectsTradePerformanceSummaryWithDifferentTradeCount() {
        SwingV1TradePerformanceSummary oneBreakEvenTradeSummary =
                new SwingV1TradePerformanceSummary(
                        1,
                        0,
                        0,
                        1,
                        0L,
                        0L,
                        0L,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        null,
                        null,
                        SwingV1ProfitFactor.noProfitOrLoss(),
                        0L,
                        null,
                        0L
                );

        assertThatThrownBy(() -> new SwingV1BacktestReport(
                request(SYMBOL),
                runResult(SYMBOL, true),
                summary(1),
                terminalEstimate(INITIAL_CASH_AMOUNT_KRW),
                List.of(),
                oneBreakEvenTradeSummary
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "tradePerformanceSummary count must match "
                                + "completed trades."
                );
    }

    @Test
    void preservesConcentrationCalculatedFromCompletedTrades() {
        SwingV1BacktestReport report = reportWithCompletedTrades();

        assertThat(report.completedTrades()).hasSize(3);
        assertThat(report.tradePerformanceSummary().largestWinningTradeNetProfitAmountKrw())
                .isEqualTo(2_000L);
        assertThat(report.tradePerformanceSummary().largestWinningTradeProfitShare())
                .isEqualByComparingTo(BigDecimal.valueOf(2L).divide(
                        BigDecimal.valueOf(3L), MathContext.DECIMAL128
                ));
        assertThat(report.tradePerformanceSummary()
                .netProfitLossExcludingLargestWinningTradeAmountKrw()).isZero();
    }

    @Test
    void rejectsLargestWinningProfitDifferentFromCompletedTrades() {
        SwingV1BacktestReport report = reportWithCompletedTrades();
        SwingV1TradePerformanceSummary incorrectSummary = tradeSummary(
                3_000L, 1_000L, 1_800L, new BigDecimal("0.6"), 200L
        );

        assertThatThrownBy(() -> withTradeSummary(report, incorrectSummary))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "tradePerformanceSummary largest winning profit must "
                                + "match completed trades."
                );
    }

    @Test
    void rejectsIncorrectWinningTotalEvenWhenNetProfitAndLargestWinnerMatch() {
        SwingV1BacktestReport report = reportWithCompletedTrades();
        SwingV1TradePerformanceSummary incorrectSummary = tradeSummary(
                4_000L, 2_000L, 2_000L, new BigDecimal("0.5"), 0L
        );

        assertThatThrownBy(() -> withTradeSummary(report, incorrectSummary))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "tradePerformanceSummary winning profit must match "
                                + "completed trades."
                );
    }

    private SwingV1BacktestReport withTradeSummary(
            SwingV1BacktestReport report,
            SwingV1TradePerformanceSummary tradeSummary
    ) {
        return new SwingV1BacktestReport(
                report.request(),
                report.runResult(),
                report.performanceSummary(),
                report.terminalLiquidationEstimate(),
                report.completedTrades(),
                tradeSummary
        );
    }

    private SwingV1TradePerformanceSummary tradeSummary(
            long winningProfit,
            long losingLoss,
            long largestProfit,
            BigDecimal profitShare,
            long excludedProfit
    ) {
        return new SwingV1TradePerformanceSummary(
                3, 2, 1, 0,
                2_000L, winningProfit, losingLoss,
                BigDecimal.valueOf(2L).divide(
                        BigDecimal.valueOf(3L), MathContext.DECIMAL128
                ),
                BigDecimal.valueOf(2_000L).divide(
                        BigDecimal.valueOf(3L), MathContext.DECIMAL128
                ),
                BigDecimal.valueOf(winningProfit).divide(BigDecimal.TWO),
                BigDecimal.valueOf(losingLoss),
                SwingV1ProfitFactor.calculated(BigDecimal.valueOf(winningProfit)
                        .divide(BigDecimal.valueOf(losingLoss), MathContext.DECIMAL128)),
                largestProfit,
                profitShare,
                excludedProfit
        );
    }

    private SwingV1BacktestReport reportWithCompletedTrades() {
        List<LocalDate> signalDates = List.of(
                LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 22),
                LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 24),
                LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 29)
        );
        List<Long> prices = List.of(10_000L, 11_000L, 10_000L, 12_000L, 10_000L, 9_000L);
        List<SwingV1BacktestStepResult> steps = new ArrayList<>();
        BacktestPortfolioState state = portfolioState;
        BacktestPortfolioTransitionService transitionService =
                new BacktestPortfolioTransitionService();
        for (int index = 0; index < signalDates.size(); index++) {
            InvestmentAction action = index % 2 == 0
                    ? InvestmentAction.BUY : InvestmentAction.SELL;
            LocalDate signalDate = signalDates.get(index);
            LocalDate fillDate = signalDate.plusDays(1);
            long price = prices.get(index);
            DailyOpenFillApproximation fill = new DailyOpenFillApproximation(
                    BacktestFillType.DAILY_OPEN_FILL_APPROXIMATION,
                    SYMBOL,
                    signalDate,
                    fillDate,
                    new TradeCostCalculator().calculate(costModel(), action, 1L, price)
            );
            BacktestPortfolioTransitionResult transition = transitionService.apply(state, fill);
            BacktestPortfolioState after = transition.portfolioState();
            long positionAmount = action == InvestmentAction.BUY ? price : 0L;
            steps.add(SwingV1BacktestStepResult.transitioned(
                    signalDate,
                    fillDate,
                    new InvestmentDecision(action, SYMBOL, 1L, price, "Backtest order."),
                    fill,
                    transition,
                    state,
                    new BacktestEquitySnapshot(
                            fillDate,
                            fillDate.atTime(9, 10).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                            after.cashAmountKrw(),
                            positionAmount,
                            Math.addExact(after.cashAmountKrw(), positionAmount)
                    )
            ));
            state = after;
        }
        SwingV1BacktestRunRequest request = new SwingV1BacktestRunRequest(
                STRATEGY_IDENTITY, SYMBOL,
                signalDates.getFirst(), signalDates.getLast(),
                portfolioState, costModel()
        );
        SwingV1BacktestRunResult runResult = new SwingV1BacktestRunResult(
                STRATEGY_IDENTITY, SYMBOL,
                signalDates.getFirst(), signalDates.getLast(),
                portfolioState, state, steps
        );
        List<SwingV1CompletedTrade> completedTrades =
                new SwingV1CompletedTradeExtractor().extract(steps);
        return new SwingV1BacktestReport(
                request,
                runResult,
                new BacktestPerformanceCalculator().calculate(portfolioState, runResult.equityCurve()),
                terminalEstimate(state.cashAmountKrw()),
                completedTrades,
                new SwingV1TradePerformanceCalculator().calculate(completedTrades)
        );
    }

    private SwingV1BacktestRunRequest request(String symbol) {
        return new SwingV1BacktestRunRequest(
                STRATEGY_IDENTITY,
                symbol,
                SIGNAL_DATE,
                SIGNAL_DATE,
                portfolioState,
                costModel()
        );
    }

    private SwingV1BacktestRunResult runResult(
            String symbol,
            boolean includeEquitySnapshot
    ) {
        List<SwingV1BacktestStepResult> steps = includeEquitySnapshot
                ? List.of(holdStep())
                : List.of();
        return new SwingV1BacktestRunResult(
                STRATEGY_IDENTITY,
                symbol,
                SIGNAL_DATE,
                SIGNAL_DATE,
                portfolioState,
                portfolioState,
                steps
        );
    }

    private SwingV1BacktestStepResult holdStep() {
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
                        INITIAL_CASH_AMOUNT_KRW,
                        0L,
                        INITIAL_CASH_AMOUNT_KRW
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

    private SwingV1TerminalLiquidationEstimate terminalEstimate(
            long finalEquityAmountKrw
    ) {
        long netProfitAmountKrw = Math.subtractExact(
                finalEquityAmountKrw,
                INITIAL_CASH_AMOUNT_KRW
        );
        BigDecimal totalReturnRate = BigDecimal
                .valueOf(netProfitAmountKrw)
                .divide(
                        BigDecimal.valueOf(INITIAL_CASH_AMOUNT_KRW),
                        MathContext.DECIMAL128
                );
        return new SwingV1TerminalLiquidationEstimate(
                INITIAL_CASH_AMOUNT_KRW,
                finalEquityAmountKrw,
                0L,
                finalEquityAmountKrw,
                netProfitAmountKrw,
                totalReturnRate
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
                SwingV1ProfitFactor.noCompletedTrades(),
                0L,
                null,
                0L
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
