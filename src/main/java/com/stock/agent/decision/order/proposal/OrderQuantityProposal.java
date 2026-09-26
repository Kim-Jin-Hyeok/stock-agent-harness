package com.stock.agent.decision.order.proposal;

import java.util.Objects;

public record OrderQuantityProposal(
        OrderDecisionIntent intent,
        Long quantity,
        String reason
) {
    public OrderQuantityProposal {
        Objects.requireNonNull(intent, "intent must not be null.");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank.");
        }

        switch (intent) {
            case EXECUTE_ORDER -> {
                if (quantity == null || quantity <= 0) {
                    throw new IllegalArgumentException(
                            "EXECUTE_ORDER quantity must be positive."
                    );
                }
            }
            case HOLD -> {
                if (quantity != null) {
                    throw new IllegalArgumentException(
                            "HOLD quantity must be null."
                    );
                }
            }
        }
    }

    public static OrderQuantityProposal execute(
            long quantity,
            String reason
    ) {
        return new OrderQuantityProposal(
                OrderDecisionIntent.EXECUTE_ORDER,
                quantity,
                reason
        );
    }

    public static OrderQuantityProposal hold(String reason) {
        return new OrderQuantityProposal(
                OrderDecisionIntent.HOLD,
                null,
                reason
        );
    }
}
