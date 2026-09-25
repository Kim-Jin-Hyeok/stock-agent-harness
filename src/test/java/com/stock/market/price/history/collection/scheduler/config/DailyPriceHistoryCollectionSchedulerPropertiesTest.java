package com.stock.market.price.history.collection.scheduler.config;

import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyPriceHistoryCollectionSchedulerPropertiesTest {

    @Test
    void acceptsSchedulerSettings() {
        DailyPriceHistoryCollectionSchedulerProperties properties =
                properties(true, "0 15 20 * * MON-FRI", "Asia/Seoul");

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.schedulerZoneId())
                .isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    void rejectsBlankCron() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(true, " ", "Asia/Seoul"))
                .withMessage(
                        "Daily price history collection scheduler cron "
                                + "must not be blank."
                );
    }

    @Test
    void rejectsInvalidCron() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(
                        true,
                        "invalid",
                        "Asia/Seoul"
                ));
    }

    @Test
    void rejectsBlankZoneId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(
                        true,
                        "0 15 20 * * MON-FRI",
                        " "
                ))
                .withMessage(
                        "Daily price history collection scheduler zoneId "
                                + "must not be blank."
                );
    }

    @Test
    void rejectsInvalidZoneId() {
        assertThatThrownBy(() -> properties(
                true,
                "0 15 20 * * MON-FRI",
                "invalid-zone"
        ))
                .isInstanceOf(DateTimeException.class);
    }

    private DailyPriceHistoryCollectionSchedulerProperties properties(
            boolean enabled,
            String cron,
            String zoneId
    ) {
        return new DailyPriceHistoryCollectionSchedulerProperties(
                enabled,
                cron,
                zoneId
        );
    }
}
