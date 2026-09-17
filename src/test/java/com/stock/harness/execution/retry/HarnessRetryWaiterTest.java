package com.stock.harness.execution.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarnessRetryWaiterTest {

    @Test
    void returnsImmediatelyWhenDelayIsZero() {
        HarnessRetryWaiter waiter = new HarnessRetryWaiter(
                new HarnessRetryProperties(Duration.ZERO)
        );

        waiter.waitBeforeRetry();
    }

    @Test
    void restoresInterruptStatusWhenWaitIsInterrupted() {
        HarnessRetryWaiter waiter = new HarnessRetryWaiter(
                new HarnessRetryProperties(Duration.ofSeconds(1))
        );
        Thread.currentThread().interrupt();

        try {
            assertThatThrownBy(waiter::waitBeforeRetry)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Tool retry wait interrupted.")
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
