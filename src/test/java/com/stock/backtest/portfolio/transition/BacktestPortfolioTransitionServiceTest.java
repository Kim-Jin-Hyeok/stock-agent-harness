package com.stock.backtest.portfolio.transition;

import com.stock.agent.InvestmentAction;
import com.stock.backtest.execution.fill.BacktestFillType;
import com.stock.backtest.execution.fill.daily.DailyOpenFillApproximation;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.BacktestPosition;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionReasonCode;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionResult;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionStatus;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BacktestPortfolioTransitionServiceTest {
    private static final String SYMBOL = "005930";
    private static final LocalDate DECISION_DATE =
            LocalDate.of(2026, 9, 28);
    private static final LocalDate FILL_DATE =
            LocalDate.of(2026, 9, 29);

    private final TradeCostCalculator tradeCostCalculator =
            new TradeCostCalculator();
    private final BacktestPortfolioTransitionService service =
            new BacktestPortfolioTransitionService();

    @Test
    void appliesBuySettlementAndCreatesPosition() {
        BacktestPortfolioState currentState =
                BacktestPortfolioState.withCash(1_000_000L);

        BacktestPortfolioTransitionResult result = service.apply(
                currentState,
                fill(InvestmentAction.BUY, 10L)
        );

        assertThat(result.status())
                .isEqualTo(BacktestPortfolioTransitionStatus.APPLIED);
        assertThat(result.reasonCode())
                .isEqualTo(BacktestPortfolioTransitionReasonCode.FILL_APPLIED);
        assertThat(result.portfolioState().cashAmountKrw())
                .isEqualTo(292_293L);
        assertThat(result.portfolioState().positions())
                .containsExactly(new BacktestPosition(
                        SYMBOL,
                        10L,
                        70_700L
                ));
        assertThat(currentState.cashAmountKrw()).isEqualTo(1_000_000L);
        assertThat(currentState.positions()).isEmpty();
    }

    @Test
    void mergesAdditionalBuyUsingWeightedExecutionPrice() {
        BacktestPortfolioState currentState = new BacktestPortfolioState(
                2_000_000L,
                List.of(new BacktestPosition(SYMBOL, 5L, 68_000L))
        );

        BacktestPortfolioTransitionResult result = service.apply(
                currentState,
                fill(InvestmentAction.BUY, 10L)
        );

        assertThat(result.portfolioState().cashAmountKrw())
                .isEqualTo(1_292_293L);
        assertThat(result.portfolioState().positions())
                .containsExactly(new BacktestPosition(
                        SYMBOL,
                        15L,
                        69_800L
                ));
    }

    @Test
    void appliesPartialSellSettlementAndPreservesAverageExecutionPrice() {
        BacktestPortfolioState currentState = new BacktestPortfolioState(
                100_000L,
                List.of(new BacktestPosition(SYMBOL, 15L, 69_800L))
        );

        BacktestPortfolioTransitionResult result = service.apply(
                currentState,
                fill(InvestmentAction.SELL, 10L)
        );

        assertThat(result.portfolioState().cashAmountKrw())
                .isEqualTo(782_570L);
        assertThat(result.portfolioState().positions())
                .containsExactly(new BacktestPosition(
                        SYMBOL,
                        5L,
                        69_800L
                ));
    }

    @Test
    void removesPositionAfterFullSell() {
        BacktestPortfolioState currentState = new BacktestPortfolioState(
                100_000L,
                List.of(new BacktestPosition(SYMBOL, 10L, 69_800L))
        );

        BacktestPortfolioTransitionResult result = service.apply(
                currentState,
                fill(InvestmentAction.SELL, 10L)
        );

        assertThat(result.portfolioState().cashAmountKrw())
                .isEqualTo(782_570L);
        assertThat(result.portfolioState().positions()).isEmpty();
    }

    @Test
    void rejectsBuyWhenSettlementExceedsAvailableCash() {
        BacktestPortfolioState currentState =
                BacktestPortfolioState.withCash(707_706L);

        BacktestPortfolioTransitionResult result = service.apply(
                currentState,
                fill(InvestmentAction.BUY, 10L)
        );

        assertThat(result.status())
                .isEqualTo(BacktestPortfolioTransitionStatus.REJECTED);
        assertThat(result.reasonCode()).isEqualTo(
                BacktestPortfolioTransitionReasonCode.INSUFFICIENT_CASH
        );
        assertThat(result.portfolioState()).isSameAs(currentState);
        assertThat(result.reason()).contains(
                "required=707707",
                "available=707706"
        );
    }

    @Test
    void rejectsSellWhenPositionDoesNotExist() {
        BacktestPortfolioState currentState =
                BacktestPortfolioState.withCash(1_000_000L);

        BacktestPortfolioTransitionResult result = service.apply(
                currentState,
                fill(InvestmentAction.SELL, 1L)
        );

        assertThat(result.reasonCode()).isEqualTo(
                BacktestPortfolioTransitionReasonCode.POSITION_NOT_FOUND
        );
        assertThat(result.portfolioState()).isSameAs(currentState);
    }

    @Test
    void rejectsSellWhenQuantityExceedsPosition() {
        BacktestPortfolioState currentState = new BacktestPortfolioState(
                100_000L,
                List.of(new BacktestPosition(SYMBOL, 5L, 69_800L))
        );

        BacktestPortfolioTransitionResult result = service.apply(
                currentState,
                fill(InvestmentAction.SELL, 10L)
        );

        assertThat(result.reasonCode()).isEqualTo(
                BacktestPortfolioTransitionReasonCode
                        .INSUFFICIENT_POSITION_QUANTITY
        );
        assertThat(result.portfolioState()).isSameAs(currentState);
        assertThat(result.reason()).contains(
                "requested=10",
                "available=5"
        );
    }

    @Test
    void rejectsNullTransitionInput() {
        BacktestPortfolioState currentState =
                BacktestPortfolioState.withCash(1_000_000L);
        DailyOpenFillApproximation fill = fill(
                InvestmentAction.BUY,
                1L
        );

        assertThatThrownBy(() -> service.apply(null, fill))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("currentState must not be null.");
        assertThatThrownBy(() -> service.apply(currentState, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("fill must not be null.");
    }

    private DailyOpenFillApproximation fill(
            InvestmentAction action,
            long quantity
    ) {
        return new DailyOpenFillApproximation(
                BacktestFillType.DAILY_OPEN_FILL_APPROXIMATION,
                SYMBOL,
                DECISION_DATE,
                FILL_DATE,
                tradeCostCalculator.calculate(
                        costModel(),
                        action,
                        quantity,
                        70_000L
                )
        );
    }

    private TradeCostModel costModel() {
        return new TradeCostModel(
                "BACKTEST_COST_V1",
                1,
                new BigDecimal("0.001"),
                new BigDecimal("0.002"),
                new BigDecimal("0.003"),
                new BigDecimal("0.01"),
                new BigDecimal("0.02")
        );
    }
}
