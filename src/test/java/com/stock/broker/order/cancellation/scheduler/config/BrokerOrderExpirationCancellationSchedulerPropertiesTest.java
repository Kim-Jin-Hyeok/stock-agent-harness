package com.stock.broker.order.cancellation.scheduler.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BrokerOrderExpirationCancellationSchedulerPropertiesTest {

    @Test
    void createsSchedulerProperties() {
        BrokerOrderExpirationCancellationSchedulerProperties properties =
                new BrokerOrderExpirationCancellationSchedulerProperties(
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
                        new BrokerOrderExpirationCancellationSchedulerProperties(
                                true,
                                0L
                        ))
                .withMessage(
                        "Broker order expiration cancellation scheduler "
                                + "fixedDelayMs must be positive."
                );
    }
}
