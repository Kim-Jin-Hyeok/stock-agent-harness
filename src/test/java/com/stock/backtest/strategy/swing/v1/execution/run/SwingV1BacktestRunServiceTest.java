package com.stock.backtest.strategy.swing.v1.execution.run;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.backtest.execution.fill.BacktestFillType;
import com.stock.backtest.execution.fill.daily.DailyOpenFillApproximation;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.BacktestPosition;
import com.stock.backtest.portfolio.transition.result.BacktestPortfolioTransitionResult;
import com.stock.backtest.strategy.swing.v1.execution.SwingV1BacktestStepRequest;
import com.stock.backtest.strategy.swing.v1.execution.SwingV1BacktestStepService;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepResult;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
import com.stock.strategy.data.history.StrategyDailyPriceHistoryPolicy;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SwingV1BacktestRunServiceTest {
    private static final String SYMBOL = "005930";
    private static final int HISTORY_LIMIT = 120;
    private static final LocalDate FIRST_SIGNAL_DATE =
            LocalDate.of(2026, 9, 28);
    private static final LocalDate SECOND_SIGNAL_DATE =
            LocalDate.of(2026, 9, 29);
    private static final LocalDate THIRD_SIGNAL_DATE =
            LocalDate.of(2026, 9, 30);
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );

    private final DailyPriceHistoryQueryService queryService = mock(
            DailyPriceHistoryQueryService.class
    );
    private final StrategyDailyPriceHistoryPolicy historyPolicy = mock(
            StrategyDailyPriceHistoryPolicy.class
    );
    private final SwingV1BacktestStepService stepService = mock(
            SwingV1BacktestStepService.class
    );
    private final SwingV1BacktestRunService service =
            new SwingV1BacktestRunService(
                    queryService,
                    historyPolicy,
                    stepService
            );

    @Test
    void executesSignalDatesInOrderAndCarriesPortfolioState() {
        SwingV1BacktestRunRequest request = request();
        BacktestPortfolioState purchasedState =
                purchasedPortfolioState();
        DailyPriceHistory firstHistory = historyThrough(
                FIRST_SIGNAL_DATE
        );
        DailyPriceHistory secondHistory = historyThrough(
                SECOND_SIGNAL_DATE
        );
        SwingV1BacktestStepResult firstStep = executedBuy(
                request.initialPortfolioState(),
                purchasedState
        );
        SwingV1BacktestStepResult secondStep = hold(
                SECOND_SIGNAL_DATE,
                THIRD_SIGNAL_DATE,
                purchasedState
        );
        when(queryService.getDailyPriceHistory(signalRangeRequest()))
                .thenReturn(new DailyPriceHistory(
                        SYMBOL,
                        List.of(
                                bar(FIRST_SIGNAL_DATE),
                                bar(SECOND_SIGNAL_DATE)
                        )
                ));
        when(historyPolicy.getLatestBarCount(STRATEGY_IDENTITY))
                .thenReturn(HISTORY_LIMIT);
        when(queryService.getLatestDailyPriceHistoryAtOrBefore(
                SYMBOL,
                FIRST_SIGNAL_DATE,
                HISTORY_LIMIT
        )).thenReturn(firstHistory);
        when(queryService.getLatestDailyPriceHistoryAtOrBefore(
                SYMBOL,
                SECOND_SIGNAL_DATE,
                HISTORY_LIMIT
        )).thenReturn(secondHistory);
        when(stepService.execute(any()))
                .thenReturn(firstStep, secondStep);

        SwingV1BacktestRunResult result = service.execute(request);

        assertThat(result.steps()).containsExactly(firstStep, secondStep);
        assertThat(result.finalPortfolioState())
                .isSameAs(purchasedState);

        ArgumentCaptor<SwingV1BacktestStepRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        SwingV1BacktestStepRequest.class
                );
        verify(stepService, times(2))
                .execute(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(SwingV1BacktestStepRequest::signalDate)
                .containsExactly(FIRST_SIGNAL_DATE, SECOND_SIGNAL_DATE);
        assertThat(requestCaptor.getAllValues().get(0).dailyPriceHistory())
                .isSameAs(firstHistory);
        assertThat(requestCaptor.getAllValues().get(1).dailyPriceHistory())
                .isSameAs(secondHistory);
        assertThat(requestCaptor.getAllValues().get(0)
                .dailyPriceHistory().bars().getLast().tradingDate())
                .isEqualTo(FIRST_SIGNAL_DATE);
        assertThat(requestCaptor.getAllValues().get(1)
                .dailyPriceHistory().bars().getLast().tradingDate())
                .isEqualTo(SECOND_SIGNAL_DATE);
        assertThat(requestCaptor.getAllValues().get(0).portfolioState())
                .isSameAs(request.initialPortfolioState());
        assertThat(requestCaptor.getAllValues().get(1).portfolioState())
                .isSameAs(purchasedState);
    }

    @Test
    void stopsAfterNoNextDailyBarResult() {
        SwingV1BacktestRunRequest request = request();
        SwingV1BacktestStepResult noNextDailyBar =
                SwingV1BacktestStepResult.noNextDailyBar(
                        FIRST_SIGNAL_DATE,
                        SYMBOL,
                        request.initialPortfolioState()
                );
        when(queryService.getDailyPriceHistory(signalRangeRequest()))
                .thenReturn(new DailyPriceHistory(
                        SYMBOL,
                        List.of(
                                bar(FIRST_SIGNAL_DATE),
                                bar(SECOND_SIGNAL_DATE)
                        )
                ));
        when(historyPolicy.getLatestBarCount(STRATEGY_IDENTITY))
                .thenReturn(HISTORY_LIMIT);
        when(queryService.getLatestDailyPriceHistoryAtOrBefore(
                SYMBOL,
                FIRST_SIGNAL_DATE,
                HISTORY_LIMIT
        )).thenReturn(historyThrough(FIRST_SIGNAL_DATE));
        when(stepService.execute(any()))
                .thenReturn(noNextDailyBar);

        SwingV1BacktestRunResult result = service.execute(request);

        assertThat(result.steps()).containsExactly(noNextDailyBar);
        assertThat(result.finalPortfolioState())
                .isSameAs(request.initialPortfolioState());
        verify(queryService, never())
                .getLatestDailyPriceHistoryAtOrBefore(
                        SYMBOL,
                        SECOND_SIGNAL_DATE,
                        HISTORY_LIMIT
                );
        verify(stepService).execute(any());
    }

    @Test
    void returnsInitialPortfolioWhenSignalRangeIsEmpty() {
        SwingV1BacktestRunRequest request = request();
        when(queryService.getDailyPriceHistory(signalRangeRequest()))
                .thenReturn(new DailyPriceHistory(SYMBOL, List.of()));
        when(historyPolicy.getLatestBarCount(STRATEGY_IDENTITY))
                .thenReturn(HISTORY_LIMIT);

        SwingV1BacktestRunResult result = service.execute(request);

        assertThat(result.steps()).isEmpty();
        assertThat(result.finalPortfolioState())
                .isSameAs(request.initialPortfolioState());
        verifyNoInteractions(stepService);
    }

    private SwingV1BacktestRunRequest request() {
        return new SwingV1BacktestRunRequest(
                STRATEGY_IDENTITY,
                SYMBOL,
                FIRST_SIGNAL_DATE,
                SECOND_SIGNAL_DATE,
                BacktestPortfolioState.withCash(1_000_000L),
                costModel()
        );
    }

    private DailyPriceHistoryRequest signalRangeRequest() {
        return new DailyPriceHistoryRequest(
                SYMBOL,
                FIRST_SIGNAL_DATE,
                SECOND_SIGNAL_DATE
        );
    }

    private DailyPriceHistory historyThrough(LocalDate signalDate) {
        return new DailyPriceHistory(
                SYMBOL,
                signalDate.equals(FIRST_SIGNAL_DATE)
                        ? List.of(bar(FIRST_SIGNAL_DATE))
                        : List.of(
                                bar(FIRST_SIGNAL_DATE),
                                bar(SECOND_SIGNAL_DATE)
                        )
        );
    }

    private SwingV1BacktestStepResult executedBuy(
            BacktestPortfolioState before,
            BacktestPortfolioState after
    ) {
        InvestmentDecision decision = new InvestmentDecision(
                InvestmentAction.BUY,
                SYMBOL,
                1L,
                70_000L,
                "Buy signal."
        );
        DailyOpenFillApproximation fill =
                new DailyOpenFillApproximation(
                        BacktestFillType.DAILY_OPEN_FILL_APPROXIMATION,
                        SYMBOL,
                        FIRST_SIGNAL_DATE,
                        SECOND_SIGNAL_DATE,
                        new TradeCostCalculator().calculate(
                                costModel(),
                                InvestmentAction.BUY,
                                1L,
                                70_000L
                        )
                );
        return SwingV1BacktestStepResult.transitioned(
                FIRST_SIGNAL_DATE,
                SECOND_SIGNAL_DATE,
                decision,
                fill,
                BacktestPortfolioTransitionResult.applied(after),
                before
        );
    }

    private SwingV1BacktestStepResult hold(
            LocalDate signalDate,
            LocalDate decisionDate,
            BacktestPortfolioState state
    ) {
        return SwingV1BacktestStepResult.hold(
                signalDate,
                decisionDate,
                new InvestmentDecision(
                        InvestmentAction.HOLD,
                        null,
                        null,
                        null,
                        "No signal."
                ),
                state
        );
    }

    private BacktestPortfolioState purchasedPortfolioState() {
        return new BacktestPortfolioState(
                930_000L,
                List.of(new BacktestPosition(
                        SYMBOL,
                        1L,
                        70_000L
                ))
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
