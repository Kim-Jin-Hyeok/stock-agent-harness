package com.stock.agent.decision.swing.v1.quantity.result;

import com.stock.agent.InvestmentAction;
import com.stock.risk.capacity.OrderQuantityCapacity;

import java.math.BigDecimal;
import java.util.Objects;

public record SwingV1OrderQuantityResult(
        InvestmentAction action,
        String symbol,
        long riskBudgetKrw,
        BigDecimal riskPerShareKrw,
        long riskBasedQuantity,
        OrderQuantityCapacity orderCapacity,
        long finalQuantity,
        SwingV1OrderQuantityReasonCode reasonCode,
        String reason
) {
    public SwingV1OrderQuantityResult {
        Objects.requireNonNull(action, "action must not be null.");
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        if (riskBudgetKrw < 0
                || riskBasedQuantity < 0
                || finalQuantity < 0) {
            throw new IllegalArgumentException(
                    "Quantity result values must not be negative."
            );
        }
        Objects.requireNonNull(
                riskPerShareKrw,
                "riskPerShareKrw must not be null."
        );
        if (riskPerShareKrw.signum() < 0) {
            throw new IllegalArgumentException(
                    "riskPerShareKrw must not be negative."
            );
        }
        Objects.requireNonNull(
                orderCapacity,
                "orderCapacity must not be null."
        );
        Objects.requireNonNull(reasonCode, "reasonCode must not be null.");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank.");
        }

        validateCapacity(action, symbol, orderCapacity);
        validateQuantity(
                action,
                riskBudgetKrw,
                riskPerShareKrw,
                riskBasedQuantity,
                orderCapacity,
                finalQuantity
        );
        validateReasonCode(
                action,
                riskPerShareKrw,
                riskBasedQuantity,
                orderCapacity,
                finalQuantity,
                reasonCode
        );
    }

    public boolean canOrder() {
        return finalQuantity > 0;
    }

    private static void validateCapacity(
            InvestmentAction action,
            String symbol,
            OrderQuantityCapacity orderCapacity
    ) {
        if (action != orderCapacity.action()) {
            throw new IllegalArgumentException(
                    "action must match orderCapacity action."
            );
        }
        if (!symbol.equals(orderCapacity.symbol())) {
            throw new IllegalArgumentException(
                    "symbol must match orderCapacity symbol."
            );
        }
    }

    private static void validateQuantity(
            InvestmentAction action,
            long riskBudgetKrw,
            BigDecimal riskPerShareKrw,
            long riskBasedQuantity,
            OrderQuantityCapacity orderCapacity,
            long finalQuantity
    ) {
        switch (action) {
            case BUY -> {
                long expectedFinalQuantity = Math.min(
                        riskBasedQuantity,
                        orderCapacity.maxAllowedQuantity()
                );
                if (finalQuantity != expectedFinalQuantity) {
                    throw new IllegalArgumentException(
                            "BUY finalQuantity must match the minimum "
                                    + "quantity limit."
                    );
                }
            }
            case SELL -> {
                validateNoRiskSizing(
                        riskBudgetKrw,
                        riskPerShareKrw,
                        riskBasedQuantity
                );
                if (finalQuantity
                        != orderCapacity.maxAllowedQuantity()) {
                    throw new IllegalArgumentException(
                            "SELL finalQuantity must match order capacity."
                    );
                }
            }
            case HOLD -> {
                validateNoRiskSizing(
                        riskBudgetKrw,
                        riskPerShareKrw,
                        riskBasedQuantity
                );
                if (finalQuantity != 0L) {
                    throw new IllegalArgumentException(
                            "HOLD finalQuantity must be zero."
                    );
                }
            }
        }
    }

    private static void validateNoRiskSizing(
            long riskBudgetKrw,
            BigDecimal riskPerShareKrw,
            long riskBasedQuantity
    ) {
        if (riskBudgetKrw != 0L
                || riskPerShareKrw.signum() != 0
                || riskBasedQuantity != 0L) {
            throw new IllegalArgumentException(
                    "Non-BUY result must not contain risk sizing."
            );
        }
    }

    private static void validateReasonCode(
            InvestmentAction action,
            BigDecimal riskPerShareKrw,
            long riskBasedQuantity,
            OrderQuantityCapacity orderCapacity,
            long finalQuantity,
            SwingV1OrderQuantityReasonCode reasonCode
    ) {
        SwingV1OrderQuantityReasonCode expectedReasonCode = switch (action) {
            case BUY -> expectedBuyReasonCode(
                    riskPerShareKrw,
                    riskBasedQuantity,
                    orderCapacity,
                    finalQuantity
            );
            case SELL -> finalQuantity > 0
                    ? SwingV1OrderQuantityReasonCode.FULL_POSITION_EXIT
                    : SwingV1OrderQuantityReasonCode.ORDER_CAPACITY_UNAVAILABLE;
            case HOLD -> SwingV1OrderQuantityReasonCode.HOLD_NO_ORDER;
        };
        if (reasonCode != expectedReasonCode) {
            throw new IllegalArgumentException(
                    "reasonCode must match quantity result."
            );
        }
    }

    private static SwingV1OrderQuantityReasonCode expectedBuyReasonCode(
            BigDecimal riskPerShareKrw,
            long riskBasedQuantity,
            OrderQuantityCapacity orderCapacity,
            long finalQuantity
    ) {
        if (riskPerShareKrw.signum() == 0) {
            return SwingV1OrderQuantityReasonCode.ATR_RISK_UNAVAILABLE;
        }
        if (riskBasedQuantity == 0L) {
            return SwingV1OrderQuantityReasonCode
                    .ATR_RISK_BUDGET_INSUFFICIENT;
        }
        if (orderCapacity.maxAllowedQuantity() == 0L) {
            return SwingV1OrderQuantityReasonCode
                    .ORDER_CAPACITY_UNAVAILABLE;
        }
        if (finalQuantity == riskBasedQuantity) {
            return SwingV1OrderQuantityReasonCode.ATR_RISK_LIMITED_BUY;
        }
        return SwingV1OrderQuantityReasonCode
                .HARNESS_CAPACITY_LIMITED_BUY;
    }
}
