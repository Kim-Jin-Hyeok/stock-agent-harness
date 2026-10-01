package com.stock.backtest.comparison.buyandhold.calculation;

import com.stock.agent.InvestmentAction;
import com.stock.backtest.comparison.buyandhold.model.BuyAndHoldBacktestRequest;
import com.stock.backtest.comparison.buyandhold.model.BuyAndHoldBacktestResult;
import com.stock.backtest.performance.metric.BacktestPerformanceCalculator;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuyAndHoldBacktestCalculatorTest {
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 9, 1);
    private static final TradeCostModel ZERO_COST = costModel("0", "0", "0");
    private final TradeCostCalculator tradeCostCalculator = new TradeCostCalculator();
    private final BuyAndHoldBacktestCalculator calculator =
            new BuyAndHoldBacktestCalculator(
                    tradeCostCalculator,
                    new BacktestPerformanceCalculator()
            );

    @Test
    void buysWholeSharesOnceAndRetainsUnspentCash() {
        BuyAndHoldBacktestResult result = calculate(
                1_000L, "0.3", ZERO_COST, 110L, 120L
        );

        assertThat(result.boughtQuantity()).isEqualTo(2L);
        assertThat(result.finalPortfolioState().cashAmountKrw()).isEqualTo(780L);
        assertThat(result.finalPortfolioState().positions()).hasSize(1);
        assertThat(result.performanceSummary().finalEquityAmountKrw())
                .isEqualTo(1_020L);
        assertThat(result.liquidationAdjustedTotalReturnRate())
                .isEqualByComparingTo("0.02");
        assertThat(result.equityCurve()).extracting(snapshot -> snapshot.evaluatedAt())
                .containsExactlyElementsOf(result.request().valuationInstants());
    }

    @Test
    void includesRoundedCommissionAndSlippageInBuyBudget() {
        BuyAndHoldBacktestResult result = calculate(
                1_000L, "1", costModel("0.01", "0.01", "0.01"), 100L, 110L
        );

        assertThat(result.boughtQuantity()).isEqualTo(9L);
        assertThat(result.buyCostCalculation().executionPriceKrw()).isEqualTo(101L);
        assertThat(result.buyCostCalculation().commissionAmountKrw()).isEqualTo(10L);
        assertThat(result.buyCostCalculation().settlementAmountKrw()).isEqualTo(919L);
        assertThat(result.finalPortfolioState().cashAmountKrw()).isEqualTo(81L);
        assertThat(result.performanceSummary().finalEquityAmountKrw()).isEqualTo(1_071L);
        assertThat(result.terminalSellCostCalculation().executionPriceKrw())
                .isEqualTo(108L);
        assertThat(result.estimatedTerminalLiquidationCostAmountKrw()).isEqualTo(38L);
        assertThat(result.liquidationAdjustedFinalEquityAmountKrw()).isEqualTo(1_033L);
        assertThat(result.liquidationAdjustedNetProfitAmountKrw()).isEqualTo(33L);
    }

    @Test
    void remainsCashOnlyIfFirstPurchaseCannotAffordOneShare() {
        BuyAndHoldBacktestResult result = calculate(
                100L, "1", costModel("0.01", "0", "0"), 100L, 1L
        );

        assertThat(result.boughtQuantity()).isZero();
        assertThat(result.buyCostCalculation()).isNull();
        assertThat(result.terminalSellCostCalculation()).isNull();
        assertThat(result.finalPortfolioState().positions()).isEmpty();
        assertThat(result.equityCurve()).allSatisfy(snapshot ->
                assertThat(snapshot.totalAssetAmountKrw()).isEqualTo(100L)
        );
        assertThat(result.estimatedTerminalLiquidationCostAmountKrw()).isZero();
        assertThat(result.liquidationAdjustedTotalReturnRate()).isEqualByComparingTo("0");
    }

    @Test
    void roundsAllocationBudgetDownWithoutBorrowingCash() {
        BuyAndHoldBacktestResult result = calculate(999L, "0.1", ZERO_COST, 100L);

        assertThat(result.request().buyBudgetAmountKrw()).isEqualTo(99L);
        assertThat(result.boughtQuantity()).isZero();
        assertThat(result.finalPortfolioState().cashAmountKrw()).isEqualTo(999L);
    }

    @Test
    void calculatesPeakToTroughDrawdownOnTheSameOpenValuations() {
        BuyAndHoldBacktestResult result = calculate(
                1_000L, "1", ZERO_COST, 100L, 120L, 90L, 110L
        );

        assertThat(result.performanceSummary().maxDrawdownAmountKrw()).isEqualTo(300L);
        assertThat(result.performanceSummary().maxDrawdownRate())
                .isEqualByComparingTo("0.25");
        assertThat(result.performanceSummary().maxDrawdownPeakDate())
                .isEqualTo(FIRST_DATE.plusDays(1));
        assertThat(result.performanceSummary().maxDrawdownTroughDate())
                .isEqualTo(FIRST_DATE.plusDays(2));
    }

    @Test
    void reportsNegativeReturnOnFallingPrices() {
        BuyAndHoldBacktestResult result = calculate(1_000L, "1", ZERO_COST, 100L, 80L);

        assertThat(result.liquidationAdjustedNetProfitAmountKrw()).isEqualTo(-200L);
        assertThat(result.liquidationAdjustedTotalReturnRate()).isEqualByComparingTo("-0.2");
        assertThat(result.performanceSummary().maxDrawdownRate()).isEqualByComparingTo("0.2");
    }

    @Test
    void reportsZeroProfitAndDrawdownOnFlatPricesWithoutCosts() {
        BuyAndHoldBacktestResult result = calculate(
                1_000L, "1", ZERO_COST, 100L, 100L, 100L
        );

        assertThat(result.liquidationAdjustedNetProfitAmountKrw()).isZero();
        assertThat(result.performanceSummary().maxDrawdownAmountKrw()).isZero();
        assertThat(result.performanceSummary().maxDrawdownPeakDate()).isNull();
        assertThat(result.performanceSummary().maxDrawdownTroughDate()).isNull();
    }

    @Test
    void deductsTerminalSellCostOnlyOnceEvenForOneValuationDay() {
        BuyAndHoldBacktestResult result = calculate(
                1_000L, "1", costModel("0.01", "0.01", "0.01"), 100L
        );

        assertThat(result.performanceSummary().netProfitAmountKrw()).isEqualTo(-19L);
        assertThat(result.estimatedTerminalLiquidationCostAmountKrw()).isEqualTo(27L);
        assertThat(result.liquidationAdjustedNetProfitAmountKrw()).isEqualTo(-46L);
        assertThat(result.finalPortfolioState().positions().getFirst().quantity())
                .isEqualTo(9L);
        assertThat(result.liquidationAdjustedFinalEquityAmountKrw()).isEqualTo(
                result.finalPortfolioState().cashAmountKrw()
                        + result.terminalSellCostCalculation().settlementAmountKrw()
        );
    }

    @Test
    void futurePricesAndSameDayCloseDoNotChangeEntryQuantity() {
        BuyAndHoldBacktestRequest request = request(1_000L, "1", ZERO_COST, 2);
        DailyPriceHistory first = history(100L, 120L);
        DailyPriceHistory changed = new DailyPriceHistory("005930", List.of(
                new DailyPriceBar(FIRST_DATE, 100L, 10_000L, 1L, 9_000L, 999L),
                new DailyPriceBar(FIRST_DATE.plusDays(1), 1L, 1L, 1L, 1L, 0L)
        ));

        BuyAndHoldBacktestResult baseline = calculator.calculate(request, first);
        BuyAndHoldBacktestResult altered = calculator.calculate(request, changed);

        assertThat(altered.buyCostCalculation()).isEqualTo(baseline.buyCostCalculation());
        assertThat(altered.finalPortfolioState()).isEqualTo(baseline.finalPortfolioState());
        assertThat(altered.equityCurve().getFirst()).isEqualTo(baseline.equityCurve().getFirst());
    }

    @Test
    void rejectsMissingExtraAndDifferentValuationDates() {
        BuyAndHoldBacktestRequest request = request(1_000L, "1", ZERO_COST, 3);
        for (DailyPriceHistory invalid : List.of(
                history(100L, 100L),
                history(100L, 100L, 100L, 100L),
                new DailyPriceHistory("005930", List.of(
                        bar(0, 100L), bar(2, 100L), bar(3, 100L)
                )),
                new DailyPriceHistory("005930", List.of())
        )) {
            assertThatThrownBy(() -> calculator.calculate(request, invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("History trading dates must exactly match valuation dates.");
        }
    }

    @Test
    void rejectsWrongSymbolAndDuplicateHistoryDates() {
        assertThatThrownBy(() -> calculator.calculate(
                request(1_000L, "1", ZERO_COST, 1),
                new DailyPriceHistory("000660", List.of(bar(0, 100L)))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("history symbol must match request symbol.");
        assertThatThrownBy(() -> new DailyPriceHistory(
                "005930", List.of(bar(0, 100L), bar(0, 100L))
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void selectsMaximumAffordableQuantityAcrossSmallBudgets() {
        TradeCostModel costs = costModel("0.015", "0.02", "0.01");
        for (long cash = 1L; cash <= 300L; cash++) {
            long expected = 0L;
            for (long quantity = 1L; quantity <= cash / 7L; quantity++) {
                if (tradeCostCalculator.calculate(
                        costs, InvestmentAction.BUY, quantity, 7L
                ).settlementAmountKrw() <= cash) {
                    expected = quantity;
                }
            }
            assertThat(calculate(cash, "1", costs, 7L).boughtQuantity())
                    .as("cash=%s", cash)
                    .isEqualTo(expected);
        }
    }

    @Test
    void handlesLargeBudgetsWithoutQuantitySearchOverflow() {
        BuyAndHoldBacktestResult result = calculate(Long.MAX_VALUE, "1", ZERO_COST, 1L);

        assertThat(result.boughtQuantity()).isEqualTo(Long.MAX_VALUE);
        assertThat(result.finalPortfolioState().cashAmountKrw()).isZero();
    }

    @Test
    void propagatesValuationOverflowInsteadOfWrappingAssets() {
        assertThatThrownBy(() -> calculate(Long.MAX_VALUE, "1", ZERO_COST, 1L, 2L))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void rejectsNullInputsAndDependencies() {
        assertThatThrownBy(() -> calculator.calculate(null, history(100L)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.calculate(
                request(1_000L, "1", ZERO_COST, 1), null
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BuyAndHoldBacktestCalculator(
                null, new BacktestPerformanceCalculator()
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BuyAndHoldBacktestCalculator(
                tradeCostCalculator, null
        )).isInstanceOf(NullPointerException.class);
    }

    private BuyAndHoldBacktestResult calculate(
            long cash, String ratio, TradeCostModel costs, long... prices
    ) {
        return calculator.calculate(
                request(cash, ratio, costs, prices.length), history(prices)
        );
    }

    private BuyAndHoldBacktestRequest request(
            long cash, String ratio, TradeCostModel costs, int size
    ) {
        return new BuyAndHoldBacktestRequest(
                "005930", cash, new BigDecimal(ratio),
                IntStream.range(0, size)
                        .mapToObj(index -> FIRST_DATE.plusDays(index)
                                .atTime(LocalTime.of(9, 10))
                                .atZone(ZoneId.of("Asia/Seoul")).toInstant())
                        .toList(),
                costs
        );
    }

    private DailyPriceHistory history(long... prices) {
        return new DailyPriceHistory("005930", IntStream.range(0, prices.length)
                .mapToObj(index -> bar(index, prices[index]))
                .toList());
    }

    private DailyPriceBar bar(int offset, long price) {
        return new DailyPriceBar(
                FIRST_DATE.plusDays(offset), price, price, price, price, 1_000L
        );
    }

    private static TradeCostModel costModel(String commission, String tax, String slippage) {
        return new TradeCostModel(
                "TEST", 1, new BigDecimal(commission), new BigDecimal(commission),
                new BigDecimal(tax), new BigDecimal(slippage), new BigDecimal(slippage)
        );
    }
}
