package com.stock.broker.order.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerOrderPropertiesTest {

    @Test
    void createsPropertiesWithPositiveValidity() {
        BrokerOrderProperties properties = new BrokerOrderProperties(
                Duration.ofMinutes(5)
        );

        assertThat(properties.validity()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void rejectsNullZeroOrNegativeValidity() {
        assertThatThrownBy(() -> new BrokerOrderProperties(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Broker order validity must be positive.");
        assertThatThrownBy(() -> new BrokerOrderProperties(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Broker order validity must be positive.");
        assertThatThrownBy(() -> new BrokerOrderProperties(
                Duration.ofSeconds(-1)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Broker order validity must be positive.");
    }
}
