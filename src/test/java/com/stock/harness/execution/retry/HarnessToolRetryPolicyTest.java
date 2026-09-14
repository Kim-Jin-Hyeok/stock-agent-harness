package com.stock.harness.execution.retry;

import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarnessToolRetryPolicyTest {

    @Test
    void retriesExecutionFailureWhenRetryRemains() {
        HarnessToolRetryPolicy policy = new HarnessToolRetryPolicy(1);

        assertThat(policy.shouldRetry(executionFailed(), 0)).isTrue();
    }

    @Test
    void doesNotRetryWhenRetryIsExhausted() {
        HarnessToolRetryPolicy policy = new HarnessToolRetryPolicy(1);

        assertThat(policy.shouldRetry(executionFailed(), 1)).isFalse();
    }

    @Test
    void doesNotRetryUnsupportedToolFailure() {
        HarnessToolRetryPolicy policy = new HarnessToolRetryPolicy(1);

        assertThat(policy.shouldRetry(
                HarnessToolExecutionResult.notSupported(HarnessToolType.GET_CURRENT_PRICE),
                0
        )).isFalse();
    }

    @Test
    void rejectsNegativeMaxRetries() {
        assertThatThrownBy(() -> new HarnessToolRetryPolicy(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxRetries must not be negative.");
    }

    private HarnessToolExecutionResult executionFailed() {
        return HarnessToolExecutionResult.executionFailed(
                HarnessToolType.GET_CURRENT_PRICE,
                "Broker timeout"
        );
    }
}
