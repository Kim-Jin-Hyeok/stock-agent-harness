package com.stock.trade.execution.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TradeExecutionPropertiesTest {

    @Test
    void createsPropertiesWithExecutionMode() {
        TradeExecutionProperties properties = new TradeExecutionProperties(
                TradeExecutionMode.VIRTUAL
        );

        assertThat(properties.mode()).isEqualTo(TradeExecutionMode.VIRTUAL);
    }

    @Test
    void rejectsMissingExecutionMode() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TradeExecutionProperties(null))
                .withMessage("Trade execution mode must not be null.");
    }
}
