package com.stock.harness.execution.limit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarnessProviderCallBudgetTest {

    @Test
    void consumesCallsUntilLimit() {
        HarnessProviderCallBudget budget = new HarnessProviderCallBudget(2);

        assertThat(budget.tryConsume()).isTrue();
        assertThat(budget.tryConsume()).isTrue();
        assertThat(budget.tryConsume()).isFalse();
        assertThat(budget.usedCalls()).isEqualTo(2);
    }

    @Test
    void rejectsNegativeMaxCalls() {
        assertThatThrownBy(() -> new HarnessProviderCallBudget(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxCalls must not be negative.");
    }
}
