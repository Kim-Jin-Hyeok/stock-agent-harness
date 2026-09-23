package com.stock.broker.order.scheduler.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BrokerOrderReconciliationSchedulerPropertiesTest {

    @Test
    void createsSchedulerProperties() {
        BrokerOrderReconciliationSchedulerProperties properties =
                new BrokerOrderReconciliationSchedulerProperties(
                        true,
                        10_000L
                );

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.fixedDelayMs()).isEqualTo(10_000L);
    }

    @Test
    void rejectsNonPositiveFixedDelay() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new BrokerOrderReconciliationSchedulerProperties(
                                true,
                                0L
                        ))
                .withMessage(
                        "Broker order reconciliation scheduler fixedDelayMs "
                                + "must be positive."
                );
    }
}
