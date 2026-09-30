package com.stock.backtest.strategy.swing.v1.report;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.performance.metric.BacktestPerformanceSummary;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepResult;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunRequest;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunResult;
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
                performanceSummary
        );

        assertThat(report.request()).isSameAs(request);
        assertThat(report.runResult()).isSameAs(runResult);
        assertThat(report.performanceSummary())
                .isSameAs(performanceSummary);
    }

    @Test
    void rejectsRunResultForDifferentCandidateSymbol() {
        SwingV1BacktestRunRequest request = request(SYMBOL);
        SwingV1BacktestRunResult runResult = runResult("000660", true);

        assertThatThrownBy(() -> new SwingV1BacktestReport(
                request,
                runResult,
                summary(1)
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
                summary(1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("runResult equityCurve must not be empty.");
    }

    @Test
    void rejectsPerformanceSummaryWithDifferentObservationCount() {
        assertThatThrownBy(() -> new SwingV1BacktestReport(
                request(SYMBOL),
                runResult(SYMBOL, true),
                summary(2)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "performanceSummary observation count must match "
                                + "runResult."
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
