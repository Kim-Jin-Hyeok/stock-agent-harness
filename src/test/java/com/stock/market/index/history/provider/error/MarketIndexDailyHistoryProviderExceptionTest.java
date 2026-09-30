package com.stock.market.index.history.provider.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MarketIndexDailyHistoryProviderExceptionTest {

    @Test
    void exposesFailureType() {
        MarketIndexDailyHistoryProviderException exception =
                new MarketIndexDailyHistoryProviderException(
                        MarketIndexDailyHistoryProviderFailureType.TEMPORARY,
                        "Temporary failure."
                );

        assertThat(exception.failureType()).isEqualTo(
                MarketIndexDailyHistoryProviderFailureType.TEMPORARY
        );
    }

    @Test
    void rejectsNullFailureType() {
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new MarketIndexDailyHistoryProviderException(
                                null,
                                "Failure."
                        ))
                .withMessage("failureType must not be null.");
    }
}
