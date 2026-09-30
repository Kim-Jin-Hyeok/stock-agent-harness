package com.stock.market.index.history.collection.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketIndexDailyHistoryBootstrapPropertiesTest {

    @Test
    void acceptsEnabledFlag() {
        assertThat(
                new MarketIndexDailyHistoryBootstrapProperties(true).enabled()
        ).isTrue();
        assertThat(
                new MarketIndexDailyHistoryBootstrapProperties(false).enabled()
        ).isFalse();
    }
}
