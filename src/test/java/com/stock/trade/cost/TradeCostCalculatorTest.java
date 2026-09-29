package com.stock.trade.cost;

import com.stock.agent.InvestmentAction;
import com.stock.trade.cost.model.TradeCostCalculation;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeCostCalculatorTest {
    private final TradeCostCalculator calculator =
            new TradeCostCalculator();

    @Test
    void calculatesBuyCommissionAndAdverseSlippage() {
        TradeCostModel costModel = costModel();

        TradeCostCalculation result = calculator.calculate(
                costModel,
                InvestmentAction.BUY,
                10L,
                10_000L
        );

        assertThat(result.costModel()).isSameAs(costModel);
        assertThat(result.action()).isEqualTo(InvestmentAction.BUY);
        assertThat(result.quantity()).isEqualTo(10L);
        assertThat(result.referencePriceKrw()).isEqualTo(10_000L);
        assertThat(result.executionPriceKrw()).isEqualTo(10_100L);
        assertThat(result.referenceOrderAmountKrw()).isEqualTo(100_000L);
        assertThat(result.executionOrderAmountKrw()).isEqualTo(101_000L);
        assertThat(result.commissionAmountKrw()).isEqualTo(101L);
        assertThat(result.taxAmountKrw()).isZero();
        assertThat(result.slippageAmountKrw()).isEqualTo(1_000L);
        assertThat(result.totalCostAmountKrw()).isEqualTo(1_101L);
        assertThat(result.settlementAmountKrw()).isEqualTo(101_101L);
    }

    @Test
    void calculatesSellCommissionTaxAndAdverseSlippage() {
        TradeCostModel costModel = costModel();

        TradeCostCalculation result = calculator.calculate(
                costModel,
                InvestmentAction.SELL,
                10L,
                10_000L
        );

        assertThat(result.costModel()).isSameAs(costModel);
        assertThat(result.action()).isEqualTo(InvestmentAction.SELL);
        assertThat(result.executionPriceKrw()).isEqualTo(9_800L);
        assertThat(result.referenceOrderAmountKrw()).isEqualTo(100_000L);
        assertThat(result.executionOrderAmountKrw()).isEqualTo(98_000L);
        assertThat(result.commissionAmountKrw()).isEqualTo(196L);
        assertThat(result.taxAmountKrw()).isEqualTo(294L);
        assertThat(result.slippageAmountKrw()).isEqualTo(2_000L);
        assertThat(result.totalCostAmountKrw()).isEqualTo(2_490L);
        assertThat(result.settlementAmountKrw()).isEqualTo(97_510L);
    }

    @Test
    void roundsPriceAndCostsAgainstStrategyPerformance() {
        TradeCostModel costModel = new TradeCostModel(
                "CONSERVATIVE_TEST",
                1,
                new BigDecimal("0.0001"),
                new BigDecimal("0.0001"),
                new BigDecimal("0.0001"),
                new BigDecimal("0.0001"),
                new BigDecimal("0.0001")
        );

        TradeCostCalculation buy = calculator.calculate(
                costModel,
                InvestmentAction.BUY,
                1L,
                1_000L
        );
        TradeCostCalculation sell = calculator.calculate(
                costModel,
                InvestmentAction.SELL,
                1L,
                1_000L
        );

        assertThat(buy.executionPriceKrw()).isEqualTo(1_001L);
        assertThat(buy.commissionAmountKrw()).isEqualTo(1L);
        assertThat(sell.executionPriceKrw()).isEqualTo(999L);
        assertThat(sell.commissionAmountKrw()).isEqualTo(1L);
        assertThat(sell.taxAmountKrw()).isEqualTo(1L);
    }

    @Test
    void rejectsActionAndOrderValuesThatCannotCreateTradeCost() {
        TradeCostModel costModel = costModel();

        assertThatThrownBy(() -> calculator.calculate(
                null,
                InvestmentAction.BUY,
                1L,
                10_000L
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("costModel must not be null.");
        assertThatThrownBy(() -> calculator.calculate(
                costModel,
                null,
                1L,
                10_000L
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action must not be null.");
        assertThatThrownBy(() -> calculator.calculate(
                costModel,
                InvestmentAction.HOLD,
                1L,
                10_000L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Trade cost calculation requires BUY or SELL "
                                + "action."
                );
        assertThatThrownBy(() -> calculator.calculate(
                costModel,
                InvestmentAction.BUY,
                0L,
                10_000L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("quantity must be positive.");
        assertThatThrownBy(() -> calculator.calculate(
                costModel,
                InvestmentAction.BUY,
                1L,
                0L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("referencePriceKrw must be positive.");
    }

    @Test
    void rejectsInvalidCostModel() {
        assertThatThrownBy(() -> new TradeCostModel(
                " ",
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("modelId must not be blank.");
        assertThatThrownBy(() -> new TradeCostModel(
                "TEST",
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("modelVersion must be at least 1.");
        assertThatThrownBy(() -> new TradeCostModel(
                "TEST",
                1,
                new BigDecimal("-0.001"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "buyCommissionRate must be at least 0 and less "
                                + "than 1."
                );
        assertThatThrownBy(() -> new TradeCostModel(
                "TEST",
                1,
                BigDecimal.ZERO,
                new BigDecimal("0.6"),
                new BigDecimal("0.4"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "sellCommissionRate and sellTaxRate total must "
                                + "be less than 1."
                );
    }

    private TradeCostModel costModel() {
        return new TradeCostModel(
                "BACKTEST_COST",
                1,
                new BigDecimal("0.001"),
                new BigDecimal("0.002"),
                new BigDecimal("0.003"),
                new BigDecimal("0.01"),
                new BigDecimal("0.02")
        );
    }
}
