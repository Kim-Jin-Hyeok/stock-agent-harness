package com.stock.harness.execution.limit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarnessToolCallBudgetTest {

    @Test
    void allowsConsumptionUntilMaxCalls() {
        HarnessToolCallBudget budget = new HarnessToolCallBudget(2);

        assertThat(budget.tryConsume()).isTrue();
        assertThat(budget.tryConsume()).isTrue();
        assertThat(budget.usedCalls()).isEqualTo(2);
    }

    @Test
    void rejectsConsumptionAfterMaxCalls() {
        HarnessToolCallBudget budget = new HarnessToolCallBudget(0);

        assertThat(budget.tryConsume()).isFalse();
        assertThat(budget.usedCalls()).isZero();
    }

    @Test
    void rejectsNegativeMaxCalls() {
        assertThatThrownBy(() -> new HarnessToolCallBudget(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxCalls must not be negative.");
    }
}
