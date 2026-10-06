package com.stock.trade.execution.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TradeExecutionPropertiesTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void createsPropertiesWithExecutionModeAndOrderPermission(boolean ordersEnabled) {
        TradeExecutionProperties properties = new TradeExecutionProperties(
                TradeExecutionMode.VIRTUAL,
                ordersEnabled
        );

        assertThat(properties.mode()).isEqualTo(TradeExecutionMode.VIRTUAL);
        assertThat(properties.ordersEnabled()).isEqualTo(ordersEnabled);
    }

    @Test
    void rejectsMissingExecutionMode() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TradeExecutionProperties(null, false))
                .withMessage("Trade execution mode must not be null.");
    }

    @ParameterizedTest
    @EnumSource(TradeExecutionMode.class)
    void missingOrderPermissionBindsToFalse(TradeExecutionMode mode) {
        contextRunner(mode).run(context -> {
            assertThat(context).hasNotFailed();
            TradeExecutionProperties properties = context.getBean(
                    TradeExecutionProperties.class
            );
            assertThat(properties.mode()).isEqualTo(mode);
            assertThat(properties.ordersEnabled()).isFalse();
        });
    }

    @ParameterizedTest
    @EnumSource(TradeExecutionMode.class)
    void explicitFalseDisablesOrders(TradeExecutionMode mode) {
        contextRunner(mode)
                .withPropertyValues("trade.execution.orders-enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(TradeExecutionProperties.class)
                            .ordersEnabled()).isFalse();
                });
    }

    @ParameterizedTest
    @EnumSource(TradeExecutionMode.class)
    void explicitTrueEnablesOrders(TradeExecutionMode mode) {
        contextRunner(mode)
                .withPropertyValues("trade.execution.orders-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(TradeExecutionProperties.class)
                            .ordersEnabled()).isTrue();
                });
    }

    @Test
    void invalidOrderPermissionFailsBinding() {
        contextRunner(TradeExecutionMode.VIRTUAL)
                .withPropertyValues("trade.execution.orders-enabled=invalid")
                .run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner contextRunner(TradeExecutionMode mode) {
        return new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues("trade.execution.mode=" + mode.name());
    }

    @EnableConfigurationProperties(TradeExecutionProperties.class)
    static class PropertiesConfiguration {
    }
}
