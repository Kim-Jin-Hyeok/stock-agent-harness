package com.stock.backtest.strategy.swing.v1.execution.run;

import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.BacktestPosition;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingV1BacktestRunRequestTest {
    private static final LocalDate FROM_SIGNAL_DATE =
            LocalDate.of(2026, 9, 1);
    private static final LocalDate TO_SIGNAL_DATE =
            LocalDate.of(2026, 9, 30);

    @Test
    void acceptsSingleSymbolBacktestRunRequest() {
        BacktestPortfolioState portfolioState =
                BacktestPortfolioState.withCash(1_000_000L);

        SwingV1BacktestRunRequest request = new SwingV1BacktestRunRequest(
                swingV1Identity(),
                "005930",
                FROM_SIGNAL_DATE,
                TO_SIGNAL_DATE,
                portfolioState,
                costModel()
        );

        assertThat(request.candidateSymbol()).isEqualTo("005930");
        assertThat(request.initialPortfolioState())
                .isSameAs(portfolioState);
    }

    @Test
    void rejectsSignalDateRangeInReverseOrder() {
        assertThatThrownBy(() -> new SwingV1BacktestRunRequest(
                swingV1Identity(),
                "005930",
                TO_SIGNAL_DATE,
                FROM_SIGNAL_DATE,
                BacktestPortfolioState.withCash(1_000_000L),
                costModel()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "fromSignalDate must not be after toSignalDate."
                );
    }

    @Test
    void rejectsNonSwingV1StrategyIdentity() {
        InvestmentStrategyIdentity wrongVersion =
                new InvestmentStrategyIdentity(
                        "SWING_V1",
                        2,
                        InvestmentHorizon.SWING
                );

        assertThatThrownBy(() -> new SwingV1BacktestRunRequest(
                wrongVersion,
                "005930",
                FROM_SIGNAL_DATE,
                TO_SIGNAL_DATE,
                BacktestPortfolioState.withCash(1_000_000L),
                costModel()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "strategyIdentity must be SWING_V1 version 1."
                );
    }

    @Test
    void rejectsInitialPositionForAnotherSymbol() {
        BacktestPortfolioState portfolioState = new BacktestPortfolioState(
                1_000_000L,
                List.of(new BacktestPosition(
                        "000660",
                        1L,
                        180_000L
                ))
        );

        assertThatThrownBy(() -> new SwingV1BacktestRunRequest(
                swingV1Identity(),
                "005930",
                FROM_SIGNAL_DATE,
                TO_SIGNAL_DATE,
                portfolioState,
                costModel()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "initialPortfolioState positions must match "
                                + "candidateSymbol for single-symbol "
                                + "backtest run."
                );
    }

    private InvestmentStrategyIdentity swingV1Identity() {
        return new InvestmentStrategyIdentity(
                "SWING_V1",
                1,
                InvestmentHorizon.SWING
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
