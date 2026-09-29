package com.stock.agent.decision.swing.v1.result;

import com.stock.agent.InvestmentAction;

import java.math.BigDecimal;
import java.util.Objects;

public record SwingV1ActionPolicyResult(
        InvestmentAction action,
        SwingV1ActionReasonCode reasonCode,
        BigDecimal atrStopPriceKrw,
        String reason
) {
    public SwingV1ActionPolicyResult {
        Objects.requireNonNull(action, "action must not be null.");
        Objects.requireNonNull(
                reasonCode,
                "reasonCode must not be null."
        );
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank.");
        }

        validateAction(action, reasonCode);
        validateAtrStopPrice(reasonCode, atrStopPriceKrw);
    }

    private static void validateAction(
            InvestmentAction action,
            SwingV1ActionReasonCode reasonCode
    ) {
        InvestmentAction expectedAction = switch (reasonCode) {
            case GOLDEN_CROSS_ENTRY -> InvestmentAction.BUY;
            case ATR_INITIAL_STOP, DEAD_CROSS_EXIT -> InvestmentAction.SELL;
            case INSUFFICIENT_DAILY_PRICE_HISTORY,
                 POSITION_ALREADY_HELD,
                 NO_ENTRY_SIGNAL,
                 HOLD_POSITION -> InvestmentAction.HOLD;
        };
        if (action != expectedAction) {
            throw new IllegalArgumentException(
                    "action must match reasonCode."
            );
        }
    }

    private static void validateAtrStopPrice(
            SwingV1ActionReasonCode reasonCode,
            BigDecimal atrStopPriceKrw
    ) {
        boolean positionReason = switch (reasonCode) {
            case POSITION_ALREADY_HELD,
                 ATR_INITIAL_STOP,
                 DEAD_CROSS_EXIT,
                 HOLD_POSITION -> true;
            case INSUFFICIENT_DAILY_PRICE_HISTORY,
                 GOLDEN_CROSS_ENTRY,
                 NO_ENTRY_SIGNAL -> false;
        };

        if (positionReason && atrStopPriceKrw == null) {
            throw new IllegalArgumentException(
                    "Position result requires atrStopPriceKrw."
            );
        }
        if (!positionReason && atrStopPriceKrw != null) {
            throw new IllegalArgumentException(
                    "Non-position result must not contain atrStopPriceKrw."
            );
        }
        if (atrStopPriceKrw != null && atrStopPriceKrw.signum() < 0) {
            throw new IllegalArgumentException(
                    "atrStopPriceKrw must not be negative."
            );
        }
    }
}
