package com.stock.harness.execution.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarnessRetryPropertiesTest {

    @Test
    void allowsZeroDelay() {
        HarnessRetryProperties properties = new HarnessRetryProperties(Duration.ZERO);

        assertThat(properties.delay()).isZero();
    }

    @Test
    void rejectsNegativeDelay() {
        assertThatThrownBy(() -> new HarnessRetryProperties(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tool retry delay must not be null or negative.");
    }

    @Test
    void rejectsMissingDelay() {
        assertThatThrownBy(() -> new HarnessRetryProperties(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tool retry delay must not be null or negative.");
    }
}
