package com.stock.harness.execution.retry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class HarnessRetryWaiter {
    private final HarnessRetryProperties properties;

    public Duration waitBeforeRetry() {
        try {
            Thread.sleep(properties.delay());
            return properties.delay();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Tool retry wait interrupted.", e);
        }
    }
}
