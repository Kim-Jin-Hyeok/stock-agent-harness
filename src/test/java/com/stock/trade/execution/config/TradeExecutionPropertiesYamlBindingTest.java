package com.stock.trade.execution.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeExecutionPropertiesYamlBindingTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"false", "true"})
    void bindsOrderPermissionFromApplicationYaml(String ordersEnabled) throws IOException {
        MockEnvironment environment = environment(false, ordersEnabled);

        TradeExecutionProperties properties = bind(environment);

        assertThat(properties.mode()).isEqualTo(TradeExecutionMode.VIRTUAL);
        assertThat(properties.ordersEnabled())
                .isEqualTo(Boolean.parseBoolean(ordersEnabled));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"false", "true"})
    void paperProfilePreservesPermissionWhileKeepingVirtualMode(String ordersEnabled)
            throws IOException {
        MockEnvironment environment = environment(true, ordersEnabled)
                .withProperty("TRADE_EXECUTION_MODE", "BROKER");

        TradeExecutionProperties properties = bind(environment);

        assertThat(properties.mode()).isEqualTo(TradeExecutionMode.VIRTUAL);
        assertThat(properties.ordersEnabled())
                .isEqualTo(Boolean.parseBoolean(ordersEnabled));
    }

    @ParameterizedTest
    @EnumSource(TradeExecutionMode.class)
    void baseYamlKeepsExecutionModeSeparateFromOrderPermission(TradeExecutionMode mode)
            throws IOException {
        MockEnvironment environment = environment(false, "true")
                .withProperty("TRADE_EXECUTION_MODE", mode.name());

        TradeExecutionProperties properties = bind(environment);

        assertThat(properties.mode()).isEqualTo(mode);
        assertThat(properties.ordersEnabled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidOrderPermissionFailsYamlBinding(boolean paperProfile) throws IOException {
        MockEnvironment environment = environment(paperProfile, "invalid");

        assertThatThrownBy(() -> bind(environment))
                .isInstanceOf(BindException.class);
    }

    private MockEnvironment environment(boolean paperProfile, String ordersEnabled)
            throws IOException {
        MockEnvironment environment = new MockEnvironment();
        if (ordersEnabled != null) {
            environment.setProperty("TRADE_EXECUTION_ORDERS_ENABLED", ordersEnabled);
        }
        if (paperProfile) {
            loadYaml(environment, "application-paper-observation.yml");
            loadYaml(environment, "application-local.yml");
        }
        loadYaml(environment, "application.yml");
        return environment;
    }

    private void loadYaml(MockEnvironment environment, String resourceName)
            throws IOException {
        new YamlPropertySourceLoader()
                .load(resourceName, new ClassPathResource(resourceName))
                .forEach(environment.getPropertySources()::addLast);
    }

    private TradeExecutionProperties bind(MockEnvironment environment) {
        return Binder.get(environment)
                .bind("trade.execution", Bindable.of(TradeExecutionProperties.class))
                .orElseThrow(() -> new IllegalStateException(
                        "Trade execution must be configured."
                ));
    }
}
