package com.stock.agent.decision.swing.v1.result;

import com.stock.agent.InvestmentAction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SwingV1ActionPolicyResultTest {

    @Test
    void createsAtrStopExitResult() {
        SwingV1ActionPolicyResult result =
                new SwingV1ActionPolicyResult(
                        InvestmentAction.SELL,
                        SwingV1ActionReasonCode.ATR_INITIAL_STOP,
                        new BigDecimal("67499.00"),
                        "ATR stop reached."
                );

        assertThat(result.action()).isEqualTo(InvestmentAction.SELL);
        assertThat(result.reasonCode())
                .isEqualTo(SwingV1ActionReasonCode.ATR_INITIAL_STOP);
        assertThat(result.atrStopPriceKrw())
                .isEqualByComparingTo("67499.00");
    }

    @Test
    void rejectsActionThatDoesNotMatchReasonCode() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SwingV1ActionPolicyResult(
                        InvestmentAction.HOLD,
                        SwingV1ActionReasonCode.GOLDEN_CROSS_ENTRY,
                        null,
                        "Invalid action."
                ))
                .withMessage("action must match reasonCode.");
    }

    @Test
    void rejectsPositionResultWithoutAtrStopPrice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SwingV1ActionPolicyResult(
                        InvestmentAction.SELL,
                        SwingV1ActionReasonCode.DEAD_CROSS_EXIT,
                        null,
                        "Missing stop price."
                ))
                .withMessage(
                        "Position result requires atrStopPriceKrw."
                );
    }

    @Test
    void rejectsNonPositionResultWithAtrStopPrice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SwingV1ActionPolicyResult(
                        InvestmentAction.BUY,
                        SwingV1ActionReasonCode.GOLDEN_CROSS_ENTRY,
                        new BigDecimal("67499.00"),
                        "Unexpected stop price."
                ))
                .withMessage(
                        "Non-position result must not contain "
                                + "atrStopPriceKrw."
                );
    }
}
