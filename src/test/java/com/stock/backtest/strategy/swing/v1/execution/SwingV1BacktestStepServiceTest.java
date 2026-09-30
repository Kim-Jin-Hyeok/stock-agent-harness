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
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SwingV1BacktestStepServiceTest {
    private static final String SYMBOL = "005930";
    private static final LocalDate SIGNAL_DATE =
            LocalDate.of(2026, 9, 28);
    private static final LocalDate DECISION_DATE =
            LocalDate.of(2026, 9, 29);

    private final DailyPriceHistoryQueryService priceHistoryQueryService =
            mock(DailyPriceHistoryQueryService.class);
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
                    priceHistoryQueryService,
                    new BacktestPortfolioEvaluationContextFactory(),
                    decisionService,
                    fillService,
                    transitionService
            );

    @Test
    void usesNextDailyOpenForDecisionAndReturnsHold() {
        SwingV1BacktestStepRequest request = request();
        DailyPriceBar decisionBar = decisionBar();
        InvestmentDecision decision = new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                null,
                "No entry signal."
        );
        when(priceHistoryQueryService.getFirstDailyPriceBarAfter(
                SYMBOL,
                SIGNAL_DATE
        )).thenReturn(Optional.of(decisionBar));
        when(decisionService.decide(any())).thenReturn(decision);

        SwingV1BacktestStepResult result = service.execute(request);

        assertThat(result.status()).isEqualTo(
                SwingV1BacktestStepStatus.HOLD
        );
        assertThat(result.signalDate()).isEqualTo(SIGNAL_DATE);
        assertThat(result.decisionDate()).isEqualTo(DECISION_DATE);
        assertThat(result.portfolioStateAfter())
                .isSameAs(request.portfolioState());
        assertThat(result.equitySnapshot().valuationDate())
                .isEqualTo(DECISION_DATE);
        assertThat(result.equitySnapshot().totalAssetAmountKrw())
                .isEqualTo(1_000_000L);
        verifyNoInteractions(fillService, transitionService);

        ArgumentCaptor<SwingV1DecisionInput> inputCaptor =
                ArgumentCaptor.forClass(SwingV1DecisionInput.class);
        verify(decisionService).decide(inputCaptor.capture());
        SwingV1DecisionInput input = inputCaptor.getValue();
        assertThat(input.dailyPriceHistory().bars().getLast().tradingDate())
                .isEqualTo(SIGNAL_DATE);
        assertThat(input.candidateCurrentPrice().priceKrw())
                .isEqualTo(80_000L);
        assertThat(input.candidateCurrentPrice().observedAt())
                .isEqualTo(Instant.parse("2026-09-29T00:10:00Z"));
        assertThat(input.candidateCurrentPriceSource()).isEqualTo(
                CurrentPriceLookupSource.BACKTEST_DAILY_OPEN
        );
    }

    @Test
    void executesOrderUsingSameNextDailyOpenAsDecisionPrice() {
        SwingV1BacktestStepRequest request = request();
        DailyPriceBar decisionBar = decisionBar();
        InvestmentDecision decision = buyDecision();
        DailyOpenFillApproximation fill = fill(decisionBar);
        BacktestPortfolioState updatedState = new BacktestPortfolioState(
                920_000L,
                List.of(new BacktestPosition(SYMBOL, 1L, 80_000L))
        );
        BacktestPortfolioTransitionResult transition =
                BacktestPortfolioTransitionResult.applied(updatedState);
        when(priceHistoryQueryService.getFirstDailyPriceBarAfter(
                SYMBOL,
                SIGNAL_DATE
        )).thenReturn(Optional.of(decisionBar));
        when(decisionService.decide(any())).thenReturn(decision);
        when(fillService.approximate(
                SYMBOL,
                SIGNAL_DATE,
                decisionBar,
                InvestmentAction.BUY,
                1L,
                request.costModel()
        )).thenReturn(fill);
        when(transitionService.apply(
                request.portfolioState(),
                fill
        )).thenReturn(transition);

        SwingV1BacktestStepResult result = service.execute(request);

        assertThat(result.status()).isEqualTo(
                SwingV1BacktestStepStatus.EXECUTED
        );
        assertThat(result.decision().expectedPriceKrw())
                .isEqualTo(80_000L);
        assertThat(result.fill().tradeCostCalculation().referencePriceKrw())
                .isEqualTo(80_000L);
        assertThat(result.portfolioStateAfter()).isSameAs(updatedState);
        assertThat(result.equitySnapshot().cashAmountKrw())
                .isEqualTo(920_000L);
        assertThat(result.equitySnapshot().positionEvaluationAmountKrw())
                .isEqualTo(80_000L);
        assertThat(result.equitySnapshot().totalAssetAmountKrw())
                .isEqualTo(1_000_000L);
    }

    @Test
    void reflectsBuyCostsInEquitySnapshot() {
        TradeCostModel costModel = costModelWithBuyCosts();
        SwingV1BacktestStepRequest request = request(costModel);
        DailyPriceBar decisionBar = decisionBar();
        when(priceHistoryQueryService.getFirstDailyPriceBarAfter(
                SYMBOL,
                SIGNAL_DATE
        )).thenReturn(Optional.of(decisionBar));
        when(decisionService.decide(any())).thenReturn(buyDecision());
        SwingV1BacktestStepService costAwareService =
                new SwingV1BacktestStepService(
                        priceHistoryQueryService,
                        new BacktestPortfolioEvaluationContextFactory(),
                        decisionService,
                        new DailyOpenFillApproximationService(
                                priceHistoryQueryService,
                                new TradeCostCalculator()
                        ),
                        new BacktestPortfolioTransitionService()
                );

        SwingV1BacktestStepResult result =
                costAwareService.execute(request);

        assertThat(result.portfolioStateAfter().cashAmountKrw())
                .isEqualTo(919_119L);
        assertThat(result.equitySnapshot().cashAmountKrw())
                .isEqualTo(919_119L);
        assertThat(result.equitySnapshot().positionEvaluationAmountKrw())
                .isEqualTo(80_000L);
        assertThat(result.equitySnapshot().totalAssetAmountKrw())
                .isEqualTo(999_119L);
    }

    @Test
    void returnsNoNextDailyBarBeforeRequestingDecision() {
        SwingV1BacktestStepRequest request = request();
        when(priceHistoryQueryService.getFirstDailyPriceBarAfter(
                SYMBOL,
                SIGNAL_DATE
        )).thenReturn(Optional.empty());

        SwingV1BacktestStepResult result = service.execute(request);

        assertThat(result.status()).isEqualTo(
                SwingV1BacktestStepStatus.NO_NEXT_DAILY_BAR
        );
        assertThat(result.decisionDate()).isNull();
        assertThat(result.decision()).isNull();
        assertThat(result.equitySnapshot()).isNull();
        assertThat(result.portfolioStateAfter())
                .isSameAs(request.portfolioState());
        verifyNoInteractions(decisionService, fillService, transitionService);
    }

    @Test
    void returnsRejectedTransitionWithoutChangingPortfolio() {
        SwingV1BacktestStepRequest request = request();
        DailyPriceBar decisionBar = decisionBar();
        InvestmentDecision decision = buyDecision();
        DailyOpenFillApproximation fill = fill(decisionBar);
        BacktestPortfolioTransitionResult transition =
                BacktestPortfolioTransitionResult.rejected(
                        BacktestPortfolioTransitionReasonCode
                                .INSUFFICIENT_CASH,
                        "Insufficient cash.",
                        request.portfolioState()
                );
        when(priceHistoryQueryService.getFirstDailyPriceBarAfter(
                SYMBOL,
                SIGNAL_DATE
        )).thenReturn(Optional.of(decisionBar));
        when(decisionService.decide(any())).thenReturn(decision);
        when(fillService.approximate(
                SYMBOL,
                SIGNAL_DATE,
                decisionBar,
                InvestmentAction.BUY,
                1L,
                request.costModel()
        )).thenReturn(fill);
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
        assertThat(result.equitySnapshot().totalAssetAmountKrw())
                .isEqualTo(1_000_000L);
        assertThat(result.reason()).isEqualTo("Insufficient cash.");
    }

    private SwingV1BacktestStepRequest request() {
        return request(costModel());
    }

    private SwingV1BacktestStepRequest request(
            TradeCostModel costModel
    ) {
        return new SwingV1BacktestStepRequest(
                new InvestmentStrategyIdentity(
                        "SWING_V1",
                        1,
                        InvestmentHorizon.SWING
                ),
                SYMBOL,
                SIGNAL_DATE,
                new DailyPriceHistory(
                        SYMBOL,
                        List.of(
                                historyBar(
                                        SIGNAL_DATE.minusDays(1),
                                        69_000L
                                ),
                                historyBar(SIGNAL_DATE, 70_000L)
                        )
                ),
                BacktestPortfolioState.withCash(1_000_000L),
                costModel
        );
    }

    private InvestmentDecision buyDecision() {
        return new InvestmentDecision(
                InvestmentAction.BUY,
                SYMBOL,
                1L,
                80_000L,
                "Golden cross entry."
        );
    }

    private DailyOpenFillApproximation fill(DailyPriceBar decisionBar) {
        return new DailyOpenFillApproximation(
                BacktestFillType.DAILY_OPEN_FILL_APPROXIMATION,
                SYMBOL,
                SIGNAL_DATE,
                DECISION_DATE,
                new TradeCostCalculator().calculate(
                        costModel(),
                        InvestmentAction.BUY,
                        1L,
                        decisionBar.openPriceKrw()
                )
        );
    }

    private DailyPriceBar decisionBar() {
        return new DailyPriceBar(
                DECISION_DATE,
                80_000L,
                82_000L,
                78_000L,
                81_000L,
                1_000_000L
        );
    }

    private DailyPriceBar historyBar(
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

    private TradeCostModel costModelWithBuyCosts() {
        return new TradeCostModel(
                "BACKTEST_COST_V1",
                1,
                new BigDecimal("0.001"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.01"),
                BigDecimal.ZERO
        );
    }
}
