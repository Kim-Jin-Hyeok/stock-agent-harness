package com.stock.broker.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerOrderReferenceTest {

    @Test
    void createsReferenceWithOrderAndOrganizationNumbers() {
        BrokerOrderReference reference = new BrokerOrderReference(
                "0000123456",
                "06010"
        );

        assertThat(reference.orderId()).isEqualTo("0000123456");
        assertThat(reference.organizationNumber()).isEqualTo("06010");
    }

    @Test
    void allowsMissingOrganizationNumberForOtherBrokers() {
        BrokerOrderReference reference = new BrokerOrderReference(
                "0000123456",
                null
        );

        assertThat(reference.organizationNumber()).isNull();
    }

    @Test
    void rejectsBlankOrderId() {
        assertThatThrownBy(() -> new BrokerOrderReference(" ", "06010"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("orderId must not be blank.");
    }

    @Test
    void rejectsBlankOrganizationNumberWhenPresent() {
        assertThatThrownBy(() -> new BrokerOrderReference("0000123456", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "organizationNumber must not be blank when present."
                );
    }
}
