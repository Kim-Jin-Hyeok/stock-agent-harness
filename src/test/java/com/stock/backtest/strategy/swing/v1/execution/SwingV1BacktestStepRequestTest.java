package com.stock.backtest.strategy.swing.v1.execution;

import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.BacktestPosition;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingV1BacktestStepRequestTest {
    private static final String SYMBOL = "005930";
    private static final LocalDate SIGNAL_DATE =
            LocalDate.of(2026, 9, 28);
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );

    @Test
    void acceptsHistoryThatEndsOnSignalDate() {
        DailyPriceHistory history = new DailyPriceHistory(
                SYMBOL,
                List.of(
                        bar(SIGNAL_DATE.minusDays(1)),
                        bar(SIGNAL_DATE)
                )
        );

        SwingV1BacktestStepRequest request = request(
                STRATEGY_IDENTITY,
                SYMBOL,
                history,
                BacktestPortfolioState.withCash(1_000_000L)
        );

        assertThat(request.signalDate()).isEqualTo(SIGNAL_DATE);
        assertThat(request.dailyPriceHistory()).isSameAs(history);
        assertThat(request.portfolioState().positions()).isEmpty();
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
                history(SYMBOL, SIGNAL_DATE),
                BacktestPortfolioState.withCash(1_000_000L)
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
                history("000660", SIGNAL_DATE),
                BacktestPortfolioState.withCash(1_000_000L)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "candidateSymbol must match dailyPriceHistory symbol."
                );
    }

    @Test
    void rejectsHistoryThatDoesNotEndOnSignalDate() {
        DailyPriceHistory historyWithFutureBar = new DailyPriceHistory(
                SYMBOL,
                List.of(
                        bar(SIGNAL_DATE),
                        bar(SIGNAL_DATE.plusDays(1))
                )
        );

        assertThatThrownBy(() -> request(
                STRATEGY_IDENTITY,
                SYMBOL,
                historyWithFutureBar,
                BacktestPortfolioState.withCash(1_000_000L)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "dailyPriceHistory latest tradingDate must match "
                                + "signalDate."
                );
    }

    @Test
    void rejectsPositionForAnotherSymbol() {
        BacktestPortfolioState portfolioState = new BacktestPortfolioState(
                1_000_000L,
                List.of(new BacktestPosition(
                        "000660",
                        1L,
                        180_000L
                ))
        );

        assertThatThrownBy(() -> request(
                STRATEGY_IDENTITY,
                SYMBOL,
                history(SYMBOL, SIGNAL_DATE),
                portfolioState
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "portfolioState positions must match candidateSymbol "
                                + "for single-symbol backtest step."
                );
    }

    private SwingV1BacktestStepRequest request(
            InvestmentStrategyIdentity strategyIdentity,
            String candidateSymbol,
            DailyPriceHistory history,
            BacktestPortfolioState portfolioState
    ) {
        return new SwingV1BacktestStepRequest(
                strategyIdentity,
                candidateSymbol,
                SIGNAL_DATE,
                history,
                portfolioState,
                costModel()
        );
    }

    private DailyPriceHistory history(
            String symbol,
            LocalDate tradingDate
    ) {
        return new DailyPriceHistory(
                symbol,
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
