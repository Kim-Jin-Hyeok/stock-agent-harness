package com.stock.market.price.history.provider.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DailyPriceHistoryProviderExceptionTest {

    @Test
    void exposesFailureType() {
        DailyPriceHistoryProviderException exception =
                new DailyPriceHistoryProviderException(
                        DailyPriceHistoryProviderFailureType.TEMPORARY,
                        "Temporary failure."
                );

        assertThat(exception.failureType())
                .isEqualTo(DailyPriceHistoryProviderFailureType.TEMPORARY);
    }

    @Test
    void rejectsNullFailureType() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DailyPriceHistoryProviderException(
                        null,
                        "Failure."
                ))
                .withMessage("failureType must not be null.");
    }
}
