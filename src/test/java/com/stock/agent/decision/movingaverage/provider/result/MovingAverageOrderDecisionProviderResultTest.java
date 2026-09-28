package com.stock.agent.decision.movingaverage.provider.result;

import com.stock.agent.decision.movingaverage.provider.identity.MovingAverageOrderDecisionProviderIdentity;
import com.stock.agent.decision.order.proposal.OrderQuantityProposal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovingAverageOrderDecisionProviderResultTest {

    @Test
    void createsResultWithIdentityAndProposal() {
        MovingAverageOrderDecisionProviderIdentity identity = identity();
        OrderQuantityProposal proposal = OrderQuantityProposal.hold(
                "Wait for another signal."
        );

        MovingAverageOrderDecisionProviderResult result =
                new MovingAverageOrderDecisionProviderResult(
                        identity,
                        proposal
                );

        assertThat(result.providerIdentity()).isEqualTo(identity);
        assertThat(result.proposal()).isEqualTo(proposal);
    }

    @Test
    void rejectsMissingProviderIdentity() {
        assertThatThrownBy(() ->
                new MovingAverageOrderDecisionProviderResult(
                        null,
                        OrderQuantityProposal.hold("Wait for another signal.")
                )
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("providerIdentity must not be null.");
    }

    private MovingAverageOrderDecisionProviderIdentity identity() {
        return new MovingAverageOrderDecisionProviderIdentity(
                "MAX_CAPACITY_RULE_BASED",
                1
        );
    }
}
