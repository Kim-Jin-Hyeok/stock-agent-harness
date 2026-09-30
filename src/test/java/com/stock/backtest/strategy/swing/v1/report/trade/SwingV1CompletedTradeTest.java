package com.stock.backtest.strategy.swing.v1.report.trade;

import com.stock.agent.InvestmentAction;
import com.stock.backtest.execution.fill.BacktestFillType;
import com.stock.backtest.execution.fill.daily.DailyOpenFillApproximation;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingV1CompletedTradeTest {
    private static final String SYMBOL = "005930";
    private static final LocalDate ENTRY_SIGNAL_DATE =
            LocalDate.of(2026, 9, 24);
    private static final LocalDate ENTRY_FILL_DATE =
            LocalDate.of(2026, 9, 25);
    private static final LocalDate EXIT_SIGNAL_DATE =
            LocalDate.of(2026, 9, 28);
    private static final LocalDate EXIT_FILL_DATE =
            LocalDate.of(2026, 9, 29);

    @Test
    void createsCompletedTradeFromEntryAndExitSettlements() {
        DailyOpenFillApproximation entryFill = fill(
                InvestmentAction.BUY,
                ENTRY_SIGNAL_DATE,
                ENTRY_FILL_DATE,
                10L,
                10_000L
        );
        DailyOpenFillApproximation exitFill = fill(
                InvestmentAction.SELL,
                EXIT_SIGNAL_DATE,
                EXIT_FILL_DATE,
                10L,
                12_000L
        );

        SwingV1CompletedTrade trade = SwingV1CompletedTrade.from(
                entryFill,
                exitFill
        );

        assertThat(trade.symbol()).isEqualTo(SYMBOL);
        assertThat(trade.quantity()).isEqualTo(10L);
        assertThat(trade.entryFill()).isSameAs(entryFill);
        assertThat(trade.exitFill()).isSameAs(exitFill);
        assertThat(trade.grossProfitLossAmountKrw()).isEqualTo(20_000L);
        assertThat(trade.totalCostAmountKrw()).isEqualTo(4_090L);
        assertThat(trade.netProfitLossAmountKrw()).isEqualTo(15_910L);
    }

    @Test
    void rejectsEntryAndExitWithDifferentQuantities() {
        DailyOpenFillApproximation entryFill = fill(
                InvestmentAction.BUY,
                ENTRY_SIGNAL_DATE,
                ENTRY_FILL_DATE,
                10L,
                10_000L
        );
        DailyOpenFillApproximation exitFill = fill(
                InvestmentAction.SELL,
                EXIT_SIGNAL_DATE,
                EXIT_FILL_DATE,
                9L,
                12_000L
        );

        assertThatThrownBy(() -> SwingV1CompletedTrade.from(
                entryFill,
                exitFill
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "entryFill and exitFill quantities must match trade "
                                + "quantity."
                );
    }

    @Test
    void rejectsNetProfitThatDoesNotMatchSettlements() {
        DailyOpenFillApproximation entryFill = fill(
                InvestmentAction.BUY,
                ENTRY_SIGNAL_DATE,
                ENTRY_FILL_DATE,
                10L,
                10_000L
        );
        DailyOpenFillApproximation exitFill = fill(
                InvestmentAction.SELL,
                EXIT_SIGNAL_DATE,
                EXIT_FILL_DATE,
                10L,
                12_000L
        );

        assertThatThrownBy(() -> new SwingV1CompletedTrade(
                SYMBOL,
                10L,
                entryFill,
                exitFill,
                20_000L,
                4_090L,
                20_000L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "netProfitLossAmountKrw must match settlement "
                                + "amounts and cost-adjusted gross profit."
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
                        costModel(),
                        action,
                        quantity,
                        referencePriceKrw
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
