package com.stock.backtest.strategy.swing.v1.execution;

import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingV1BacktestStepRequestTest {
    private static final String SYMBOL = "005930";
    private static final LocalDate DECISION_DATE =
            LocalDate.of(2026, 9, 29);
    private static final Instant EVALUATED_AT =
            Instant.parse("2026-09-29T06:30:00Z");
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );

    @Test
    void copiesEvaluationBarsAndAcceptsDecisionDateHistory() {
        Map<String, DailyPriceBar> evaluationBars = new HashMap<>();
        evaluationBars.put(SYMBOL, bar(DECISION_DATE));

        SwingV1BacktestStepRequest request = request(
                STRATEGY_IDENTITY,
                SYMBOL,
                new DailyPriceHistory(
                        SYMBOL,
                        List.of(
                                bar(DECISION_DATE.minusDays(1)),
                                bar(DECISION_DATE)
                        )
                ),
                evaluationBars
        );
        evaluationBars.clear();

        assertThat(request.evaluationBarsBySymbol())
                .containsOnlyKeys(SYMBOL);
        assertThatThrownBy(() -> request
                .evaluationBarsBySymbol()
                .clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNonSwingV1StrategyIdentity() {
        InvestmentStrategyIdentity wrongVersion =
                new InvestmentStrategyIdentity(
                        "SWING_V1",
                        2,
                        InvestmentHorizon.SWING
                );

        assertThatThrownBy(() -> request(
                wrongVersion,
                SYMBOL,
                history(DECISION_DATE),
                Map.of(SYMBOL, bar(DECISION_DATE))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "strategyIdentity must be SWING_V1 version 1."
                );
    }

    @Test
    void rejectsCandidateAndHistorySymbolMismatch() {
        assertThatThrownBy(() -> request(
                STRATEGY_IDENTITY,
                SYMBOL,
                new DailyPriceHistory(
                        "000660",
                        List.of(bar(DECISION_DATE))
                ),
                Map.of(SYMBOL, bar(DECISION_DATE))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "candidateSymbol must match dailyPriceHistory symbol."
                );
    }

    @Test
    void rejectsHistoryThatDoesNotEndOnDecisionDate() {
        DailyPriceHistory historyWithFutureBar = new DailyPriceHistory(
                SYMBOL,
                List.of(
                        bar(DECISION_DATE),
                        bar(DECISION_DATE.plusDays(1))
                )
        );

        assertThatThrownBy(() -> request(
                STRATEGY_IDENTITY,
                SYMBOL,
                historyWithFutureBar,
                Map.of(SYMBOL, bar(DECISION_DATE))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "dailyPriceHistory latest tradingDate must match "
                                + "decisionDate."
                );
    }

    @Test
    void rejectsEvaluationBarsWithoutCandidate() {
        assertThatThrownBy(() -> request(
                STRATEGY_IDENTITY,
                SYMBOL,
                history(DECISION_DATE),
                Map.of("000660", bar(DECISION_DATE))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "evaluationBarsBySymbol must contain candidateSymbol."
                );
    }

    private SwingV1BacktestStepRequest request(
            InvestmentStrategyIdentity strategyIdentity,
            String candidateSymbol,
            DailyPriceHistory history,
            Map<String, DailyPriceBar> evaluationBars
    ) {
        return new SwingV1BacktestStepRequest(
                strategyIdentity,
                candidateSymbol,
                DECISION_DATE,
                EVALUATED_AT,
                history,
                evaluationBars,
                BacktestPortfolioState.withCash(1_000_000L),
                costModel()
        );
    }

    private DailyPriceHistory history(LocalDate tradingDate) {
        return new DailyPriceHistory(
                SYMBOL,
                List.of(bar(tradingDate))
        );
    }

    private DailyPriceBar bar(LocalDate tradingDate) {
        return new DailyPriceBar(
                tradingDate,
                70_000L,
                72_000L,
                69_000L,
                71_000L,
                1_000_000L
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
