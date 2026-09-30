package com.stock.backtest.strategy.swing.v1.report.terminal;

import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.BacktestPosition;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingV1TerminalLiquidationCalculatorTest {
    private static final long INITIAL_EQUITY_AMOUNT_KRW = 1_000_000L;
    private static final LocalDate TERMINAL_DATE =
            LocalDate.of(2026, 9, 30);

    private final SwingV1TerminalLiquidationCalculator calculator =
            new SwingV1TerminalLiquidationCalculator(
                    new TradeCostCalculator()
            );

    @Test
    void keepsFinalEquityWhenNoPositionRemains() {
        BacktestPortfolioState finalState =
                BacktestPortfolioState.withCash(1_100_000L);

        SwingV1TerminalLiquidationEstimate estimate =
                calculator.calculate(
                        INITIAL_EQUITY_AMOUNT_KRW,
                        finalState,
                        snapshot(1_100_000L, 0L),
                        costModel()
                );

        assertThat(estimate.markToMarketFinalEquityAmountKrw())
                .isEqualTo(1_100_000L);
        assertThat(estimate.estimatedLiquidationCostAmountKrw()).isZero();
        assertThat(estimate.liquidationAdjustedFinalEquityAmountKrw())
                .isEqualTo(1_100_000L);
        assertThat(estimate.liquidationAdjustedNetProfitAmountKrw())
                .isEqualTo(100_000L);
        assertThat(estimate.liquidationAdjustedTotalReturnRate())
                .isEqualByComparingTo(new BigDecimal("0.1"));
    }

    @Test
    void deductsEstimatedSellCostsFromOpenPositionValue() {
        BacktestPortfolioState finalState = new BacktestPortfolioState(
                300_000L,
                List.of(new BacktestPosition(
                        "005930",
                        10L,
                        9_500L
                ))
        );

        SwingV1TerminalLiquidationEstimate estimate =
                calculator.calculate(
                        INITIAL_EQUITY_AMOUNT_KRW,
                        finalState,
                        snapshot(300_000L, 100_000L),
                        costModel()
                );

        assertThat(estimate.markToMarketFinalEquityAmountKrw())
                .isEqualTo(400_000L);
        assertThat(estimate.estimatedLiquidationCostAmountKrw())
                .isEqualTo(2_490L);
        assertThat(estimate.liquidationAdjustedFinalEquityAmountKrw())
                .isEqualTo(397_510L);
        assertThat(estimate.liquidationAdjustedNetProfitAmountKrw())
                .isEqualTo(-602_490L);
        assertThat(estimate.liquidationAdjustedTotalReturnRate())
                .isEqualByComparingTo(new BigDecimal("-0.60249"));
    }

    @Test
    void rejectsMultipleFinalPositions() {
        BacktestPortfolioState finalState = new BacktestPortfolioState(
                300_000L,
                List.of(
                        new BacktestPosition("005930", 10L, 10_000L),
                        new BacktestPosition("000660", 1L, 100_000L)
                )
        );

        assertThatThrownBy(() -> calculator.calculate(
                INITIAL_EQUITY_AMOUNT_KRW,
                finalState,
                snapshot(300_000L, 200_000L),
                costModel()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "finalPortfolioState must contain at most one "
                                + "position for SWING_V1 terminal "
                                + "liquidation."
                );
    }

    @Test
    void rejectsPositionEvaluationWithoutExactReferencePrice() {
        BacktestPortfolioState finalState = new BacktestPortfolioState(
                300_000L,
                List.of(new BacktestPosition(
                        "005930",
                        3L,
                        10_000L
                ))
        );

        assertThatThrownBy(() -> calculator.calculate(
                INITIAL_EQUITY_AMOUNT_KRW,
                finalState,
                snapshot(300_000L, 100_000L),
                costModel()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "terminalSnapshot position evaluation must resolve "
                                + "to an exact positive price for the final "
                                + "position."
                );
    }

    private BacktestEquitySnapshot snapshot(
            long cashAmountKrw,
            long positionEvaluationAmountKrw
    ) {
        return new BacktestEquitySnapshot(
                TERMINAL_DATE,
                TERMINAL_DATE.atTime(9, 10)
                        .atZone(ZoneId.of("Asia/Seoul"))
                        .toInstant(),
                cashAmountKrw,
                positionEvaluationAmountKrw,
                Math.addExact(
                        cashAmountKrw,
                        positionEvaluationAmountKrw
                )
        );
    }

    private TradeCostModel costModel() {
        return new TradeCostModel(
                "BACKTEST_COST_V1",
                1,
                BigDecimal.ZERO,
                new BigDecimal("0.002"),
                new BigDecimal("0.003"),
                BigDecimal.ZERO,
                new BigDecimal("0.02")
        );
    }
}
