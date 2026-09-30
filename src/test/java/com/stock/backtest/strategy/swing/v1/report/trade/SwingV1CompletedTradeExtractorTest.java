package com.stock.backtest.strategy.swing.v1.report.trade;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.backtest.execution.fill.BacktestFillType;
import com.stock.backtest.execution.fill.daily.DailyOpenFillApproximation;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.BacktestPosition;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionReasonCode;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionResult;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepResult;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingV1CompletedTradeExtractorTest {
    private static final String SYMBOL = "005930";
    private static final LocalDate ENTRY_SIGNAL_DATE =
            LocalDate.of(2026, 9, 24);
    private static final LocalDate ENTRY_FILL_DATE =
            LocalDate.of(2026, 9, 25);
    private static final LocalDate EXIT_SIGNAL_DATE =
            LocalDate.of(2026, 9, 28);
    private static final LocalDate EXIT_FILL_DATE =
            LocalDate.of(2026, 9, 29);
    private static final long INITIAL_CASH_AMOUNT_KRW = 1_000_000L;

    private final SwingV1CompletedTradeExtractor extractor =
            new SwingV1CompletedTradeExtractor();

    @Test
    void extractsOneCompletedTradeFromBuyAndSellSteps() {
        BacktestPortfolioState initialState = initialState();
        BacktestPortfolioState purchasedState = purchasedState(10L);
        BacktestPortfolioState soldState =
                BacktestPortfolioState.withCash(1_020_000L);
        SwingV1BacktestStepResult buyStep = executedStep(
                InvestmentAction.BUY,
                ENTRY_SIGNAL_DATE,
                ENTRY_FILL_DATE,
                initialState,
                purchasedState,
                10L,
                10_000L
        );
        SwingV1BacktestStepResult sellStep = executedStep(
                InvestmentAction.SELL,
                EXIT_SIGNAL_DATE,
                EXIT_FILL_DATE,
                purchasedState,
                soldState,
                10L,
                12_000L
        );

        List<SwingV1CompletedTrade> completedTrades = extractor.extract(
                List.of(buyStep, sellStep)
        );

        assertThat(completedTrades).hasSize(1);
        assertThat(completedTrades.getFirst().entryFill())
                .isSameAs(buyStep.fill());
        assertThat(completedTrades.getFirst().exitFill())
                .isSameAs(sellStep.fill());
        assertThat(completedTrades.getFirst().netProfitLossAmountKrw())
                .isEqualTo(20_000L);
    }

    @Test
    void doesNotCompleteTradeForOpenBuyEntry() {
        BacktestPortfolioState initialState = initialState();
        SwingV1BacktestStepResult buyStep = executedStep(
                InvestmentAction.BUY,
                ENTRY_SIGNAL_DATE,
                ENTRY_FILL_DATE,
                initialState,
                purchasedState(10L),
                10L,
                10_000L
        );

        List<SwingV1CompletedTrade> completedTrades = extractor.extract(
                List.of(buyStep)
        );

        assertThat(completedTrades).isEmpty();
    }

    @Test
    void ignoresRejectedAndNoExecutionSteps() {
        BacktestPortfolioState initialState = initialState();
        SwingV1BacktestStepResult rejectedStep = rejectedBuyStep(
                initialState
        );
        SwingV1BacktestStepResult noNextBarStep =
                SwingV1BacktestStepResult.noNextDailyBar(
                        EXIT_SIGNAL_DATE,
                        SYMBOL,
                        initialState
                );

        List<SwingV1CompletedTrade> completedTrades = extractor.extract(
                List.of(rejectedStep, noNextBarStep)
        );

        assertThat(completedTrades).isEmpty();
    }

    @Test
    void rejectsSellWithoutOpenBuyEntry() {
        BacktestPortfolioState purchasedState = purchasedState(10L);
        SwingV1BacktestStepResult sellStep = executedStep(
                InvestmentAction.SELL,
                EXIT_SIGNAL_DATE,
                EXIT_FILL_DATE,
                purchasedState,
                BacktestPortfolioState.withCash(1_020_000L),
                10L,
                12_000L
        );

        assertThatThrownBy(() -> extractor.extract(List.of(sellStep)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Executed SELL requires an open BUY entry.");
    }

    @Test
    void rejectsAdditionalBuyWhileEntryIsOpen() {
        BacktestPortfolioState initialState = initialState();
        BacktestPortfolioState firstPurchasedState = purchasedState(10L);
        SwingV1BacktestStepResult firstBuyStep = executedStep(
                InvestmentAction.BUY,
                ENTRY_SIGNAL_DATE,
                ENTRY_FILL_DATE,
                initialState,
                firstPurchasedState,
                10L,
                10_000L
        );
        SwingV1BacktestStepResult secondBuyStep = executedStep(
                InvestmentAction.BUY,
                EXIT_SIGNAL_DATE,
                EXIT_FILL_DATE,
                firstPurchasedState,
                purchasedState(20L),
                10L,
                10_000L
        );

        assertThatThrownBy(() -> extractor.extract(List.of(
                firstBuyStep,
                secondBuyStep
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Executed BUY must not occur while an entry is open."
                );
    }

    private SwingV1BacktestStepResult executedStep(
            InvestmentAction action,
            LocalDate signalDate,
            LocalDate fillDate,
            BacktestPortfolioState before,
            BacktestPortfolioState after,
            long quantity,
            long referencePriceKrw
    ) {
        DailyOpenFillApproximation fill = fill(
                action,
                signalDate,
                fillDate,
                quantity,
                referencePriceKrw
        );
        return SwingV1BacktestStepResult.transitioned(
                signalDate,
                fillDate,
                orderDecision(action, quantity, referencePriceKrw),
                fill,
                BacktestPortfolioTransitionResult.applied(after),
                before,
                snapshot(fillDate, after, referencePriceKrw)
        );
    }

    private SwingV1BacktestStepResult rejectedBuyStep(
            BacktestPortfolioState state
    ) {
        DailyOpenFillApproximation fill = fill(
                InvestmentAction.BUY,
                ENTRY_SIGNAL_DATE,
                ENTRY_FILL_DATE,
                10L,
                10_000L
        );
        return SwingV1BacktestStepResult.transitioned(
                ENTRY_SIGNAL_DATE,
                ENTRY_FILL_DATE,
                orderDecision(InvestmentAction.BUY, 10L, 10_000L),
                fill,
                BacktestPortfolioTransitionResult.rejected(
                        BacktestPortfolioTransitionReasonCode
                                .INSUFFICIENT_CASH,
                        "Insufficient cash.",
                        state
                ),
                state,
                snapshot(ENTRY_FILL_DATE, state, 10_000L)
        );
    }

    private DailyOpenFillApproximation fill(
            InvestmentAction action,
            LocalDate signalDate,
            LocalDate fillDate,
            long quantity,
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
                        quantity,
                        referencePriceKrw
                )
        );
    }

    private InvestmentDecision orderDecision(
            InvestmentAction action,
            long quantity,
            long expectedPriceKrw
    ) {
        return new InvestmentDecision(
                action,
                SYMBOL,
                quantity,
                expectedPriceKrw,
                "Backtest order."
        );
    }

    private BacktestEquitySnapshot snapshot(
            LocalDate valuationDate,
            BacktestPortfolioState state,
            long currentPriceKrw
    ) {
        long positionEvaluationAmountKrw = state.positions()
                .stream()
                .mapToLong(position -> Math.multiplyExact(
                        position.quantity(),
                        currentPriceKrw
                ))
                .reduce(0L, Math::addExact);
        return new BacktestEquitySnapshot(
                valuationDate,
                valuationDate.atTime(9, 10)
                        .atZone(ZoneId.of("Asia/Seoul"))
                        .toInstant(),
                state.cashAmountKrw(),
                positionEvaluationAmountKrw,
                Math.addExact(
                        state.cashAmountKrw(),
                        positionEvaluationAmountKrw
                )
        );
    }

    private BacktestPortfolioState initialState() {
        return BacktestPortfolioState.withCash(INITIAL_CASH_AMOUNT_KRW);
    }

    private BacktestPortfolioState purchasedState(long quantity) {
        return new BacktestPortfolioState(
                Math.subtractExact(
                        INITIAL_CASH_AMOUNT_KRW,
                        Math.multiplyExact(quantity, 10_000L)
                ),
                List.of(new BacktestPosition(
                        SYMBOL,
                        quantity,
                        10_000L
                ))
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
