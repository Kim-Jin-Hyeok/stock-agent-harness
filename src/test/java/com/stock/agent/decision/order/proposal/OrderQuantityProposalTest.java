package com.stock.agent.decision.order.proposal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderQuantityProposalTest {

    @Test
    void createsExecuteOrderProposalWithPositiveQuantity() {
        OrderQuantityProposal proposal = OrderQuantityProposal.execute(
                5L,
                "Order quantity is within the allowed capacity."
        );

        assertThat(proposal.intent())
                .isEqualTo(OrderDecisionIntent.EXECUTE_ORDER);
        assertThat(proposal.quantity()).isEqualTo(5L);
        assertThat(proposal.reason())
                .isEqualTo(
                        "Order quantity is within the allowed capacity."
                );
    }

    @Test
    void createsHoldProposalWithoutQuantity() {
        OrderQuantityProposal proposal = OrderQuantityProposal.hold(
                "Current conditions do not justify an order."
        );

        assertThat(proposal.intent()).isEqualTo(OrderDecisionIntent.HOLD);
        assertThat(proposal.quantity()).isNull();
    }

    @Test
    void rejectsNonPositiveExecuteOrderQuantity() {
        assertThatThrownBy(() -> OrderQuantityProposal.execute(
                0L,
                "Invalid quantity."
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EXECUTE_ORDER quantity must be positive.");
    }

    @Test
    void rejectsNullExecuteOrderQuantity() {
        assertThatThrownBy(() -> new OrderQuantityProposal(
                OrderDecisionIntent.EXECUTE_ORDER,
                null,
                "Invalid quantity."
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EXECUTE_ORDER quantity must be positive.");
    }

    @Test
    void rejectsHoldProposalWithQuantity() {
        assertThatThrownBy(() -> new OrderQuantityProposal(
                OrderDecisionIntent.HOLD,
                1L,
                "HOLD must not contain an order quantity."
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("HOLD quantity must be null.");
    }

    @Test
    void rejectsNullIntent() {
        assertThatThrownBy(() -> new OrderQuantityProposal(
                null,
                null,
                "Intent is required."
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("intent must not be null.");
    }

    @Test
    void rejectsBlankReason() {
        assertThatThrownBy(() -> OrderQuantityProposal.hold(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reason must not be blank.");
    }
}
