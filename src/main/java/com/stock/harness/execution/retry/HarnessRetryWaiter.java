package com.stock.harness.execution.retry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HarnessRetryWaiter {
    private final HarnessRetryProperties properties;

    public void waitBeforeRetry() {
        try {
            Thread.sleep(properties.delay());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Tool retry wait interrupted.", e);
        }
    }
}
