package com.stock.agent.decision.movingaverage.provider.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovingAverageOrderDecisionProviderIdentityTest {

    @Test
    void createsVersionedProviderIdentity() {
        MovingAverageOrderDecisionProviderIdentity identity =
                new MovingAverageOrderDecisionProviderIdentity(
                        "MAX_CAPACITY_RULE_BASED",
                        1
                );

        assertThat(identity.providerId())
                .isEqualTo("MAX_CAPACITY_RULE_BASED");
        assertThat(identity.providerVersion()).isEqualTo(1);
    }

    @Test
    void rejectsBlankProviderId() {
        assertThatThrownBy(() ->
                new MovingAverageOrderDecisionProviderIdentity(" ", 1)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("providerId must not be blank.");
    }

    @Test
    void rejectsProviderVersionLessThanOne() {
        assertThatThrownBy(() ->
                new MovingAverageOrderDecisionProviderIdentity(
                        "MAX_CAPACITY_RULE_BASED",
                        0
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("providerVersion must be at least 1.");
    }
}
