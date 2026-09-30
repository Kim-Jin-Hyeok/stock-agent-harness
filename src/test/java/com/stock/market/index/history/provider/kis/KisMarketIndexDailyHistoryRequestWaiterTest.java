package com.stock.market.index.history.provider.kis;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class KisMarketIndexDailyHistoryRequestWaiterTest {

    @Test
    void waitsWithZeroDelay() {
        KisMarketIndexDailyHistoryRequestWaiter waiter =
                new KisMarketIndexDailyHistoryRequestWaiter(Duration.ZERO);

        assertThatNoException().isThrownBy(waiter::waitBeforeRequest);
    }

    @Test
    void rejectsNullRequestDelay() {
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new KisMarketIndexDailyHistoryRequestWaiter(null))
                .withMessage("requestDelay must not be null.");
    }

    @Test
    void rejectsNegativeRequestDelay() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new KisMarketIndexDailyHistoryRequestWaiter(
                                Duration.ofMillis(-1)
                        ))
                .withMessage("requestDelay must not be negative.");
    }
}
