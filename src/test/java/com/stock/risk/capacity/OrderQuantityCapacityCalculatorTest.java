package com.stock.risk.capacity;

import com.stock.agent.InvestmentAction;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.risk.RiskProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderQuantityCapacityCalculatorTest {
    private static final String SYMBOL = "005930";

    private final OrderQuantityCapacityCalculator calculator =
            new OrderQuantityCapacityCalculator(
                    new RiskProperties(0.1, 0.3)
            );

    @Test
    void calculatesBuyCapacityUsingMostRestrictiveLimit() {
        OrderQuantityCapacity capacity = calculator.calculate(
                InvestmentAction.BUY,
                SYMBOL,
                100_000L,
                emptyPortfolio(10_000_000L, 10_000_000L)
        );

        assertThat(capacity.maxAffordableQuantity()).isEqualTo(100L);
        assertThat(capacity.maxOrderRatioQuantity()).isEqualTo(10L);
        assertThat(capacity.maxPositionRatioQuantity()).isEqualTo(30L);
        assertThat(capacity.maxAllowedQuantity()).isEqualTo(10L);
        assertThat(capacity.oneSharePortfolioRatio())
                .isEqualByComparingTo("0.010000");
        assertThat(capacity.canOrder()).isTrue();
    }

    @Test
    void limitsBuyCapacityByAvailableCash() {
        OrderQuantityCapacity capacity = calculator.calculate(
                InvestmentAction.BUY,
                SYMBOL,
                100_000L,
                emptyPortfolio(250_000L, 10_000_000L)
        );

        assertThat(capacity.maxAllowedQuantity()).isEqualTo(2L);
    }

    @Test
    void limitsBuyCapacityByRemainingPositionAmount() {
        PortfolioSnapshot portfolio = new PortfolioSnapshot(
                10_000_000L,
                10_000_000L,
                List.of(new PortfolioPosition(
                        SYMBOL,
                        25L,
                        100_000L,
                        2_500_000L
                ))
        );

        OrderQuantityCapacity capacity = calculator.calculate(
                InvestmentAction.BUY,
                SYMBOL,
                100_000L,
                portfolio
        );

        assertThat(capacity.currentPositionQuantity()).isEqualTo(25L);
        assertThat(capacity.maxPositionRatioQuantity()).isEqualTo(5L);
        assertThat(capacity.maxAllowedQuantity()).isEqualTo(5L);
    }

    @Test
    void returnsZeroWhenOneShareExceedsRiskOrderLimit() {
        OrderQuantityCapacity capacity = calculator.calculate(
                InvestmentAction.BUY,
                SYMBOL,
                400_000L,
                emptyPortfolio(3_333_334L, 3_333_334L)
        );

        assertThat(capacity.maxAffordableQuantity()).isEqualTo(8L);
        assertThat(capacity.maxOrderRatioQuantity()).isZero();
        assertThat(capacity.maxAllowedQuantity()).isZero();
        assertThat(capacity.oneSharePortfolioRatio())
                .isEqualByComparingTo("0.120000");
        assertThat(capacity.canOrder()).isFalse();
    }

    @Test
    void usesCurrentPositionQuantityAsSellCapacity() {
        PortfolioSnapshot portfolio = new PortfolioSnapshot(
                1_000_000L,
                3_500_000L,
                List.of(new PortfolioPosition(
                        SYMBOL,
                        25L,
                        100_000L,
                        2_500_000L
                ))
        );

        OrderQuantityCapacity capacity = calculator.calculate(
                InvestmentAction.SELL,
                SYMBOL,
                100_000L,
                portfolio
        );

        assertThat(capacity.maxAllowedQuantity()).isEqualTo(25L);
        assertThat(capacity.canOrder()).isTrue();
    }

    @Test
    void returnsZeroCapacityForHold() {
        OrderQuantityCapacity capacity = calculator.calculate(
                InvestmentAction.HOLD,
                SYMBOL,
                100_000L,
                emptyPortfolio(10_000_000L, 10_000_000L)
        );

        assertThat(capacity.maxAllowedQuantity()).isZero();
        assertThat(capacity.canOrder()).isFalse();
    }

    @Test
    void rejectsInvalidInput() {
        PortfolioSnapshot portfolio = emptyPortfolio(
                10_000_000L,
                10_000_000L
        );

        assertThatThrownBy(() -> calculator.calculate(
                null,
                SYMBOL,
                100_000L,
                portfolio
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action must not be null.");
        assertThatThrownBy(() -> calculator.calculate(
                InvestmentAction.BUY,
                " ",
                100_000L,
                portfolio
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must not be blank.");
        assertThatThrownBy(() -> calculator.calculate(
                InvestmentAction.BUY,
                SYMBOL,
                0,
                portfolio
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("currentPriceKrw must be positive.");
        assertThatThrownBy(() -> calculator.calculate(
                InvestmentAction.BUY,
                SYMBOL,
                100_000L,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("portfolioSnapshot must not be null.");
    }

    private PortfolioSnapshot emptyPortfolio(
            long cashAmountKrw,
            long totalAssetAmountKrw
    ) {
        return new PortfolioSnapshot(
                cashAmountKrw,
                totalAssetAmountKrw,
                List.of()
        );
    }
}
