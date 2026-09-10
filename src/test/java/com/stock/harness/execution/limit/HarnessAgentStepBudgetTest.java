package com.stock.harness.execution.limit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarnessAgentStepBudgetTest {

    @Test
    void allowsConsumptionUntilMaxSteps() {
        HarnessAgentStepBudget budget = new HarnessAgentStepBudget(2);

        assertThat(budget.tryConsume()).isTrue();
        assertThat(budget.tryConsume()).isTrue();
        assertThat(budget.usedSteps()).isEqualTo(2);
    }

    @Test
    void rejectsConsumptionAfterMaxSteps() {
        HarnessAgentStepBudget budget = new HarnessAgentStepBudget(1);

        assertThat(budget.tryConsume()).isTrue();
        assertThat(budget.tryConsume()).isFalse();
        assertThat(budget.usedSteps()).isEqualTo(1);
    }

    @Test
    void rejectsNonPositiveMaxSteps() {
        assertThatThrownBy(() -> new HarnessAgentStepBudget(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxSteps must be at least 1.");
    }
}
