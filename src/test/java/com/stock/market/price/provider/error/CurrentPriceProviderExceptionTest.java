package com.stock.market.price.provider.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CurrentPriceProviderExceptionTest {

    @Test
    void keepsFailureTypeMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("connection reset");

        CurrentPriceProviderException exception = new CurrentPriceProviderException(
                CurrentPriceProviderFailureType.TEMPORARY,
                "Broker request failed.",
                cause
        );

        assertThat(exception.failureType()).isEqualTo(
                CurrentPriceProviderFailureType.TEMPORARY
        );
        assertThat(exception).hasMessage("Broker request failed.");
        assertThat(exception).hasCause(cause);
    }

    @Test
    void rejectsMissingFailureType() {
        assertThatNullPointerException()
                .isThrownBy(() -> new CurrentPriceProviderException(
                        null,
                        "Broker request failed."
                ))
                .withMessage("failureType must not be null.");
    }
}
