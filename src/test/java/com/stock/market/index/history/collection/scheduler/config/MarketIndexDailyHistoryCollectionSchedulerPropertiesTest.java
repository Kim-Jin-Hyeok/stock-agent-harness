package com.stock.market.index.history.collection.scheduler.config;

import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketIndexDailyHistoryCollectionSchedulerPropertiesTest {

    @Test
    void acceptsSchedulerSettings() {
        MarketIndexDailyHistoryCollectionSchedulerProperties properties =
                properties(true, "0 25 20 * * MON-FRI", "Asia/Seoul");

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.schedulerZoneId())
                .isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    void rejectsBlankCron() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(true, " ", "Asia/Seoul"))
                .withMessage(
                        "Market index daily history collection scheduler cron "
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
                        "0 25 20 * * MON-FRI",
                        " "
                ))
                .withMessage(
                        "Market index daily history collection scheduler zoneId "
                                + "must not be blank."
                );
    }

    @Test
    void rejectsInvalidZoneId() {
        assertThatThrownBy(() -> properties(
                true,
                "0 25 20 * * MON-FRI",
                "invalid-zone"
        ))
                .isInstanceOf(DateTimeException.class);
    }

    private MarketIndexDailyHistoryCollectionSchedulerProperties properties(
            boolean enabled,
            String cron,
            String zoneId
    ) {
        return new MarketIndexDailyHistoryCollectionSchedulerProperties(
                enabled,
                cron,
                zoneId
        );
    }
}
