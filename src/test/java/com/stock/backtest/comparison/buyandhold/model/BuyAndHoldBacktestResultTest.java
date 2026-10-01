package com.stock.backtest.comparison.buyandhold.model;

import com.stock.backtest.comparison.buyandhold.calculation.BuyAndHoldBacktestCalculator;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.performance.metric.BacktestPerformanceCalculator;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuyAndHoldBacktestResultTest {
    @Test
    void copiesEquityCurveAndPreservesPurchaseEvidence() {
        BuyAndHoldBacktestResult original = result();
        List<BacktestEquitySnapshot> mutable = new ArrayList<>(original.equityCurve());
        BuyAndHoldBacktestResult copy = copy(
                original, original.finalPortfolioState(), mutable,
                original.estimatedTerminalLiquidationCostAmountKrw(),
                original.liquidationAdjustedFinalEquityAmountKrw()
        );
        mutable.clear();

        assertThat(copy).isEqualTo(original);
        assertThat(copy.boughtQuantity()).isEqualTo(9L);
        assertThatThrownBy(() -> copy.equityCurve().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDoubleDeductionOfTerminalCost() {
        BuyAndHoldBacktestResult original = result();
        assertThatThrownBy(() -> copy(
                original, original.finalPortfolioState(), original.equityCurve(),
                original.estimatedTerminalLiquidationCostAmountKrw(),
                original.liquidationAdjustedFinalEquityAmountKrw()
                        - original.estimatedTerminalLiquidationCostAmountKrw()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Liquidation-adjusted amounts and return must deduct "
                        + "terminal sell cost exactly once.");
    }

    @Test
    void rejectsLiquidationCostWithoutMatchingSellEvidence() {
        BuyAndHoldBacktestResult original = result();
        assertThatThrownBy(() -> copy(
                original, original.finalPortfolioState(), original.equityCurve(),
                original.estimatedTerminalLiquidationCostAmountKrw() + 1,
                original.liquidationAdjustedFinalEquityAmountKrw()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsChangedPortfolioAndMissingValuation() {
        BuyAndHoldBacktestResult original = result();
        assertThatThrownBy(() -> copy(
                original, BacktestPortfolioState.withCash(1_000L), original.equityCurve(),
                original.estimatedTerminalLiquidationCostAmountKrw(),
                original.liquidationAdjustedFinalEquityAmountKrw()
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> copy(
                original, original.finalPortfolioState(), List.of(),
                original.estimatedTerminalLiquidationCostAmountKrw(),
                original.liquidationAdjustedFinalEquityAmountKrw()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private BuyAndHoldBacktestResult result() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        return new BuyAndHoldBacktestCalculator(
                new TradeCostCalculator(), new BacktestPerformanceCalculator()
        ).calculate(
                new BuyAndHoldBacktestRequest(
                        "005930", 1_000L, BigDecimal.ONE,
                        List.of(date.atTime(9, 10).atZone(ZoneId.of("Asia/Seoul")).toInstant()),
                        new TradeCostModel("TEST", 1,
                                new BigDecimal("0.01"), new BigDecimal("0.01"),
                                new BigDecimal("0.01"), new BigDecimal("0.01"),
                                new BigDecimal("0.01"))
                ),
                new DailyPriceHistory("005930", List.of(
                        new DailyPriceBar(date, 100L, 100L, 100L, 100L, 1L)
                ))
        );
    }

    private BuyAndHoldBacktestResult copy(
            BuyAndHoldBacktestResult original,
            BacktestPortfolioState portfolio,
            List<BacktestEquitySnapshot> curve,
            long terminalCost,
            long adjustedFinalEquity
    ) {
        return new BuyAndHoldBacktestResult(
                original.request(), original.buyCostCalculation(), portfolio, curve,
                original.performanceSummary(), original.terminalSellCostCalculation(),
                terminalCost, adjustedFinalEquity,
                original.liquidationAdjustedNetProfitAmountKrw(),
                original.liquidationAdjustedTotalReturnRate()
        );
    }
}
