package com.stock.backtest.execution.fill.daily;

import com.stock.agent.InvestmentAction;
import com.stock.backtest.execution.fill.BacktestFillType;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DailyOpenFillApproximationServiceTest {
    private static final String SYMBOL = "005930";
    private static final LocalDate DECISION_DATE =
            LocalDate.of(2026, 9, 28);
    private static final LocalDate FILL_DATE =
            LocalDate.of(2026, 9, 29);

    private final DailyPriceHistoryQueryService queryService = mock(
            DailyPriceHistoryQueryService.class
    );
    private final TradeCostCalculator tradeCostCalculator =
            new TradeCostCalculator();
    private final DailyOpenFillApproximationService service =
            new DailyOpenFillApproximationService(
                    queryService,
                    tradeCostCalculator
            );

    @Test
    void approximatesBuyFillAtNextDailyOpenWithTradeCosts() {
        DailyPriceBar fillBar = fillBar();
        TradeCostModel costModel = costModel();
        when(queryService.getFirstDailyPriceBarAfter(
                SYMBOL,
                DECISION_DATE
        )).thenReturn(Optional.of(fillBar));

        Optional<DailyOpenFillApproximation> result = service.approximate(
                SYMBOL,
                DECISION_DATE,
                InvestmentAction.BUY,
                10L,
                costModel
        );

        assertThat(result).isPresent();
        DailyOpenFillApproximation fill = result.orElseThrow();
        assertThat(fill.fillType()).isEqualTo(
                BacktestFillType.DAILY_OPEN_FILL_APPROXIMATION
        );
        assertThat(fill.symbol()).isEqualTo(SYMBOL);
        assertThat(fill.decisionDate()).isEqualTo(DECISION_DATE);
        assertThat(fill.fillDate()).isEqualTo(FILL_DATE);
        assertThat(fill.tradeCostCalculation().costModel())
                .isSameAs(costModel);
        assertThat(fill.tradeCostCalculation().action())
                .isEqualTo(InvestmentAction.BUY);
        assertThat(fill.tradeCostCalculation().quantity()).isEqualTo(10L);
        assertThat(fill.tradeCostCalculation().referencePriceKrw())
                .isEqualTo(fillBar.openPriceKrw());
        assertThat(fill.tradeCostCalculation().executionPriceKrw())
                .isEqualTo(70_700L);
        assertThat(fill.tradeCostCalculation().settlementAmountKrw())
                .isEqualTo(707_707L);
        verify(queryService).getFirstDailyPriceBarAfter(
                SYMBOL,
                DECISION_DATE
        );
    }

    @Test
    void appliesSellCostsAgainstNextDailyOpen() {
        DailyPriceBar fillBar = fillBar();
        when(queryService.getFirstDailyPriceBarAfter(
                SYMBOL,
                DECISION_DATE
        )).thenReturn(Optional.of(fillBar));

        DailyOpenFillApproximation fill = service.approximate(
                SYMBOL,
                DECISION_DATE,
                InvestmentAction.SELL,
                10L,
                costModel()
        ).orElseThrow();

        assertThat(fill.tradeCostCalculation().action())
                .isEqualTo(InvestmentAction.SELL);
        assertThat(fill.tradeCostCalculation().referencePriceKrw())
                .isEqualTo(70_000L);
        assertThat(fill.tradeCostCalculation().executionPriceKrw())
                .isEqualTo(68_600L);
        assertThat(fill.tradeCostCalculation().commissionAmountKrw())
                .isEqualTo(1_372L);
        assertThat(fill.tradeCostCalculation().taxAmountKrw())
                .isEqualTo(2_058L);
        assertThat(fill.tradeCostCalculation().settlementAmountKrw())
                .isEqualTo(682_570L);
    }

    @Test
    void returnsEmptyWhenNextDailyPriceBarDoesNotExist() {
        when(queryService.getFirstDailyPriceBarAfter(
                SYMBOL,
                DECISION_DATE
        )).thenReturn(Optional.empty());

        Optional<DailyOpenFillApproximation> result = service.approximate(
                SYMBOL,
                DECISION_DATE,
                InvestmentAction.BUY,
                10L,
                costModel()
        );

        assertThat(result).isEmpty();
        verify(queryService).getFirstDailyPriceBarAfter(
                SYMBOL,
                DECISION_DATE
        );
    }

    @Test
    void rejectsInvalidRequestBeforeQueryingDailyPrices() {
        TradeCostModel costModel = costModel();

        assertThatThrownBy(() -> service.approximate(
                " ",
                DECISION_DATE,
                InvestmentAction.BUY,
                1L,
                costModel
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must not be blank.");
        assertThatThrownBy(() -> service.approximate(
                SYMBOL,
                null,
                InvestmentAction.BUY,
                1L,
                costModel
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("decisionDate must not be null.");
        assertThatThrownBy(() -> service.approximate(
                SYMBOL,
                DECISION_DATE,
                null,
                1L,
                costModel
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action must not be null.");
        assertThatThrownBy(() -> service.approximate(
                SYMBOL,
                DECISION_DATE,
                InvestmentAction.HOLD,
                1L,
                costModel
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Daily open fill approximation requires BUY or SELL action."
                );
        assertThatThrownBy(() -> service.approximate(
                SYMBOL,
                DECISION_DATE,
                InvestmentAction.BUY,
                0L,
                costModel
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("quantity must be positive.");
        assertThatThrownBy(() -> service.approximate(
                SYMBOL,
                DECISION_DATE,
                InvestmentAction.BUY,
                1L,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("costModel must not be null.");
        verifyNoInteractions(queryService);
    }

    private DailyPriceBar fillBar() {
        return new DailyPriceBar(
                FILL_DATE,
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
                new BigDecimal("0.001"),
                new BigDecimal("0.002"),
                new BigDecimal("0.003"),
                new BigDecimal("0.01"),
                new BigDecimal("0.02")
        );
    }
}
