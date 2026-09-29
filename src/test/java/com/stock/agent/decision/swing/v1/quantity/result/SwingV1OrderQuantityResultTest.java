package com.stock.agent.decision.swing.v1.quantity.result;

import com.stock.agent.InvestmentAction;
import com.stock.risk.capacity.OrderQuantityCapacity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SwingV1OrderQuantityResultTest {
    private static final String SYMBOL = "005930";

    @Test
    void createsAtrRiskLimitedBuyResult() {
        SwingV1OrderQuantityResult result =
                new SwingV1OrderQuantityResult(
                        InvestmentAction.BUY,
                        SYMBOL,
                        50_000L,
                        new BigDecimal("2000.0"),
                        25L,
                        buyCapacity(100L),
                        25L,
                        SwingV1OrderQuantityReasonCode
                                .ATR_RISK_LIMITED_BUY,
                        "ATR risk limit applied."
                );

        assertThat(result.finalQuantity()).isEqualTo(25L);
        assertThat(result.canOrder()).isTrue();
    }

    @Test
    void rejectsBuyQuantityThatIsNotMinimumLimit() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SwingV1OrderQuantityResult(
                        InvestmentAction.BUY,
                        SYMBOL,
                        50_000L,
                        new BigDecimal("2000.0"),
                        25L,
                        buyCapacity(20L),
                        25L,
                        SwingV1OrderQuantityReasonCode
                                .ATR_RISK_LIMITED_BUY,
                        "Invalid quantity."
                ))
                .withMessage(
                        "BUY finalQuantity must match the minimum "
                                + "quantity limit."
                );
    }

    @Test
    void rejectsReasonCodeThatDoesNotMatchLimitingRule() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SwingV1OrderQuantityResult(
                        InvestmentAction.BUY,
                        SYMBOL,
                        50_000L,
                        new BigDecimal("2000.0"),
                        25L,
                        buyCapacity(20L),
                        20L,
                        SwingV1OrderQuantityReasonCode
                                .ATR_RISK_LIMITED_BUY,
                        "Invalid reason code."
                ))
                .withMessage("reasonCode must match quantity result.");
    }

    private OrderQuantityCapacity buyCapacity(long maxAllowedQuantity) {
        return new OrderQuantityCapacity(
                InvestmentAction.BUY,
                SYMBOL,
                10_000L,
                0L,
                1_000L,
                maxAllowedQuantity,
                300L,
                maxAllowedQuantity,
                new BigDecimal("0.001000")
        );
    }
}
