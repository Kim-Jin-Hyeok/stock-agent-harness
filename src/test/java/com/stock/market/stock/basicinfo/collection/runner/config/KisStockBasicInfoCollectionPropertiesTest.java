package com.stock.market.stock.basicinfo.collection.runner.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockBasicInfoCollectionPropertiesTest {
    private static final String PREFIX = "market.stock.basic-info.collection.manual.";
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsAbsentSettingsAsDisabledWithoutDefaultingTheSymbol() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(KisStockBasicInfoCollectionProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.symbol()).isNull();
        });
    }

    @Test
    void disabledSettingsDoNotRequireAValidSymbol() {
        assertThat(new KisStockBasicInfoCollectionProperties(false, null).symbol()).isNull();
        assertThat(new KisStockBasicInfoCollectionProperties(false, "invalid").symbol()).isEqualTo("invalid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"005930", "0004Y0"})
    void bindsExactlyOneSymbolWithoutNormalization(String symbol) {
        runner.withPropertyValues(PREFIX + "enabled=true", PREFIX + "symbol=" + symbol).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(KisStockBasicInfoCollectionProperties.class))
                    .isEqualTo(new KisStockBasicInfoCollectionProperties(true, symbol));
        });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "00593", "0059300", "0004y0", " 005930", "005930 ", "005930,0004Y0", "[005930]", "005930\n"})
    void rejectsMissingMalformedOrMultipleSymbolsWhenEnabled(String symbol) {
        assertThatThrownBy(() -> new KisStockBasicInfoCollectionProperties(true, symbol))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must be exactly 6 uppercase alphanumeric characters.");
    }

    @Test
    void enabledBindingRequiresAnExplicitSymbol() {
        runner.withPropertyValues(PREFIX + "enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("symbol must be exactly 6 uppercase alphanumeric characters.");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(KisStockBasicInfoCollectionProperties.class)
    static class PropertiesConfiguration {
    }
}
