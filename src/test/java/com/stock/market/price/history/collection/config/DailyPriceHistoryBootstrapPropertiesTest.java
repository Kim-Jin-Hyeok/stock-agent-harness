package com.stock.market.price.history.collection.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DailyPriceHistoryBootstrapPropertiesTest {

    @Test
    void acceptsEnabledFlag() {
        assertThat(new DailyPriceHistoryBootstrapProperties(true).enabled())
                .isTrue();
        assertThat(new DailyPriceHistoryBootstrapProperties(false).enabled())
                .isFalse();
    }
}
