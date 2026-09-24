package com.stock.market.price.history.collection.config;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DailyPriceHistoryCollectionPropertiesTest {

    @Test
    void acceptsDailyBarAvailableAt() {
        DailyPriceHistoryCollectionProperties properties =
                new DailyPriceHistoryCollectionProperties(
                        LocalTime.of(20, 10)
                );

        assertThat(properties.dailyBarAvailableAt())
                .isEqualTo(LocalTime.of(20, 10));
    }

    @Test
    void rejectsNullDailyBarAvailableAt() {
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new DailyPriceHistoryCollectionProperties(null))
                .withMessage("dailyBarAvailableAt must not be null.");
    }
}
