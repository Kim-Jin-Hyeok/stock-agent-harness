package com.stock.market.price.history.provider.kis;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class KisDailyPriceHistoryRequestWaiterTest {

    @Test
    void waitsWithZeroDelay() {
        KisDailyPriceHistoryRequestWaiter waiter =
                new KisDailyPriceHistoryRequestWaiter(Duration.ZERO);

        assertThatNoException().isThrownBy(waiter::waitBeforeRequest);
    }

    @Test
    void rejectsNullRequestDelay() {
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new KisDailyPriceHistoryRequestWaiter(null))
                .withMessage("requestDelay must not be null.");
    }

    @Test
    void rejectsNegativeRequestDelay() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KisDailyPriceHistoryRequestWaiter(
                        Duration.ofMillis(-1)
                ))
                .withMessage("requestDelay must not be negative.");
    }
}
