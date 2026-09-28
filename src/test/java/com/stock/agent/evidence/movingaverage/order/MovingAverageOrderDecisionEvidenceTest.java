package com.stock.agent.evidence.movingaverage.order;

import com.stock.agent.InvestmentAction;
import com.stock.agent.decision.movingaverage.provider.identity.MovingAverageOrderDecisionProviderIdentity;
import com.stock.agent.decision.order.proposal.OrderQuantityProposal;
import com.stock.risk.capacity.OrderQuantityCapacity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovingAverageOrderDecisionEvidenceTest {

    @Test
    void createsEvidenceFromSignalCapacityAndProposal() {
        OrderQuantityCapacity capacity = buyCapacity();
        OrderQuantityProposal proposal = OrderQuantityProposal.execute(
                1L,
                "Execute within the allowed capacity."
        );

        MovingAverageOrderDecisionEvidence evidence =
                new MovingAverageOrderDecisionEvidence(
                        InvestmentAction.BUY,
                        capacity,
                        providerIdentity(),
                        proposal
                );

        assertThat(evidence.signalAction()).isEqualTo(InvestmentAction.BUY);
        assertThat(evidence.quantityCapacity()).isEqualTo(capacity);
        assertThat(evidence.providerIdentity())
                .isEqualTo(providerIdentity());
        assertThat(evidence.proposal()).isEqualTo(proposal);
    }

    @Test
    void rejectsSignalActionDifferentFromCapacityAction() {
        assertThatThrownBy(() -> new MovingAverageOrderDecisionEvidence(
                InvestmentAction.SELL,
                buyCapacity(),
                providerIdentity(),
                OrderQuantityProposal.hold("Wait for another signal.")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "signalAction must match quantityCapacity action."
                );
    }

    private OrderQuantityCapacity buyCapacity() {
        return new OrderQuantityCapacity(
                InvestmentAction.BUY,
                "005930",
                72_000L,
                0L,
                13L,
                1L,
                4L,
                1L,
                new BigDecimal("0.007200")
        );
    }

    private MovingAverageOrderDecisionProviderIdentity providerIdentity() {
        return new MovingAverageOrderDecisionProviderIdentity(
                "MAX_CAPACITY_RULE_BASED",
                1
        );
    }
}
