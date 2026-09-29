package com.stock.backtest.strategy.swing.v1.execution;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.agent.decision.swing.v1.SwingV1DecisionInput;
import com.stock.agent.decision.swing.v1.SwingV1DecisionService;
import com.stock.backtest.context.portfolio.BacktestPortfolioEvaluationContextFactory;
import com.stock.backtest.execution.fill.BacktestFillType;
import com.stock.backtest.execution.fill.daily.DailyOpenFillApproximation;
import com.stock.backtest.execution.fill.daily.DailyOpenFillApproximationService;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.BacktestPosition;
import com.stock.backtest.portfolio.transition.BacktestPortfolioTransitionService;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionReasonCode;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionResult;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepResult;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepStatus;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SwingV1BacktestStepServiceTest {
    private static final String SYMBOL = "005930";
    private static final LocalDate DECISION_DATE =
            LocalDate.of(2026, 9, 29);
    private static final Instant EVALUATED_AT =
            Instant.parse("2026-09-29T06:30:00Z");

    private final SwingV1DecisionService decisionService = mock(
            SwingV1DecisionService.class
    );
    private final DailyOpenFillApproximationService fillService = mock(
            DailyOpenFillApproximationService.class
    );
    private final BacktestPortfolioTransitionService transitionService =
            mock(BacktestPortfolioTransitionService.class);
    private final SwingV1BacktestStepService service =
            new SwingV1BacktestStepService(
                    new BacktestPortfolioEvaluationContextFactory(),
                    decisionService,
                    fillService,
                    transitionService
            );

    @Test
    void returnsHoldWithoutRequestingFillOrPortfolioTransition() {
        SwingV1BacktestStepRequest request = request();
        InvestmentDecision decision = new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                null,
                "No entry signal."
        );
        when(decisionService.decide(any())).thenReturn(decision);

        SwingV1BacktestStepResult result = service.execute(request);

        assertThat(result.status()).isEqualTo(
                SwingV1BacktestStepStatus.HOLD
        );
        assertThat(result.portfolioStateBefore())
                .isSameAs(request.portfolioState());
        assertThat(result.portfolioStateAfter())
                .isSameAs(request.portfolioState());
        assertThat(result.fill()).isNull();
        assertThat(result.portfolioTransition()).isNull();
        verifyNoInteractions(fillService, transitionService);

        ArgumentCaptor<SwingV1DecisionInput> inputCaptor =
                ArgumentCaptor.forClass(SwingV1DecisionInput.class);
        verify(decisionService).decide(inputCaptor.capture());
        SwingV1DecisionInput input = inputCaptor.getValue();
        assertThat(input.candidateCurrentPrice().priceKrw())
                .isEqualTo(71_000L);
        assertThat(input.candidateCurrentPriceSource()).isEqualTo(
                CurrentPriceLookupSource.BACKTEST_DAILY_CLOSE
        );
        assertThat(input.portfolioSnapshot().cashAmountKrw())
                .isEqualTo(1_000_000L);
    }

    @Test
    void executesOrderAndReturnsTransitionedPortfolio() {
        SwingV1BacktestStepRequest request = request();
        InvestmentDecision decision = buyDecision();
        DailyOpenFillApproximation fill = fill();
        BacktestPortfolioState updatedState = new BacktestPortfolioState(
                929_000L,
                List.of(new BacktestPosition(SYMBOL, 1L, 71_000L))
        );
        BacktestPortfolioTransitionResult transition =
                BacktestPortfolioTransitionResult.applied(updatedState);
        when(decisionService.decide(any())).thenReturn(decision);
        when(fillService.approximate(
                SYMBOL,
                DECISION_DATE,
                InvestmentAction.BUY,
                1L,
                request.costModel()
        )).thenReturn(Optional.of(fill));
        when(transitionService.apply(
                request.portfolioState(),
                fill
        )).thenReturn(transition);

        SwingV1BacktestStepResult result = service.execute(request);

        assertThat(result.status()).isEqualTo(
                SwingV1BacktestStepStatus.EXECUTED
        );
        assertThat(result.decision()).isSameAs(decision);
        assertThat(result.fill()).isSameAs(fill);
        assertThat(result.portfolioTransition()).isSameAs(transition);
        assertThat(result.portfolioStateAfter()).isSameAs(updatedState);
    }

    @Test
    void preservesPortfolioWhenNextDailyBarDoesNotExist() {
        SwingV1BacktestStepRequest request = request();
        when(decisionService.decide(any())).thenReturn(buyDecision());
        when(fillService.approximate(
                SYMBOL,
                DECISION_DATE,
                InvestmentAction.BUY,
                1L,
                request.costModel()
        )).thenReturn(Optional.empty());

        SwingV1BacktestStepResult result = service.execute(request);

        assertThat(result.status()).isEqualTo(
                SwingV1BacktestStepStatus.NO_NEXT_DAILY_BAR
        );
        assertThat(result.portfolioStateAfter())
                .isSameAs(request.portfolioState());
        assertThat(result.fill()).isNull();
        verifyNoInteractions(transitionService);
    }

    @Test
    void returnsRejectedTransitionWithoutChangingPortfolio() {
        SwingV1BacktestStepRequest request = request();
        InvestmentDecision decision = buyDecision();
        DailyOpenFillApproximation fill = fill();
        BacktestPortfolioTransitionResult transition =
                BacktestPortfolioTransitionResult.rejected(
                        BacktestPortfolioTransitionReasonCode
                                .INSUFFICIENT_CASH,
                        "Insufficient cash.",
                        request.portfolioState()
                );
        when(decisionService.decide(any())).thenReturn(decision);
        when(fillService.approximate(
                SYMBOL,
                DECISION_DATE,
                InvestmentAction.BUY,
                1L,
                request.costModel()
        )).thenReturn(Optional.of(fill));
        when(transitionService.apply(
                request.portfolioState(),
                fill
        )).thenReturn(transition);

        SwingV1BacktestStepResult result = service.execute(request);

        assertThat(result.status()).isEqualTo(
                SwingV1BacktestStepStatus.REJECTED
        );
        assertThat(result.portfolioStateAfter())
                .isSameAs(request.portfolioState());
        assertThat(result.reason()).isEqualTo("Insufficient cash.");
    }

    private SwingV1BacktestStepRequest request() {
        DailyPriceBar evaluationBar = bar(DECISION_DATE, 71_000L);
        return new SwingV1BacktestStepRequest(
                new InvestmentStrategyIdentity(
                        "SWING_V1",
                        1,
                        InvestmentHorizon.SWING
                ),
                SYMBOL,
                DECISION_DATE,
                EVALUATED_AT,
                new DailyPriceHistory(
                        SYMBOL,
                        List.of(
                                bar(DECISION_DATE.minusDays(1), 70_000L),
                                evaluationBar
                        )
                ),
                Map.of(SYMBOL, evaluationBar),
                BacktestPortfolioState.withCash(1_000_000L),
                costModel()
        );
    }

    private InvestmentDecision buyDecision() {
        return new InvestmentDecision(
                InvestmentAction.BUY,
                SYMBOL,
                1L,
                71_000L,
                "Golden cross entry."
        );
    }

    private DailyOpenFillApproximation fill() {
        return new DailyOpenFillApproximation(
                BacktestFillType.DAILY_OPEN_FILL_APPROXIMATION,
                SYMBOL,
                DECISION_DATE,
                DECISION_DATE.plusDays(1),
                new TradeCostCalculator().calculate(
                        costModel(),
                        InvestmentAction.BUY,
                        1L,
                        71_000L
                )
        );
    }

    private DailyPriceBar bar(
            LocalDate tradingDate,
            long closePriceKrw
    ) {
        return new DailyPriceBar(
                tradingDate,
                closePriceKrw - 1_000L,
                closePriceKrw + 1_000L,
                closePriceKrw - 2_000L,
                closePriceKrw,
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
