package com.stock.market.index.history;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketIndexDailyHistoryRequestTest {
    private static final LocalDate FROM_DATE =
            LocalDate.of(2026, 1, 2);
    private static final LocalDate TO_DATE =
            LocalDate.of(2026, 9, 30);

    @Test
    void createsValidRequest() {
        MarketIndexDailyHistoryRequest request =
                new MarketIndexDailyHistoryRequest(
                        "KOSPI",
                        FROM_DATE,
                        TO_DATE
                );

        assertThat(request.benchmarkId()).isEqualTo("KOSPI");
        assertThat(request.fromDate()).isEqualTo(FROM_DATE);
        assertThat(request.toDate()).isEqualTo(TO_DATE);
    }

    @Test
    void rejectsBlankBenchmarkId() {
        assertThatThrownBy(() -> new MarketIndexDailyHistoryRequest(
                " ",
                FROM_DATE,
                TO_DATE
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("benchmarkId must not be blank.");
    }

    @Test
    void rejectsNullDate() {
        assertThatNullPointerException()
                .isThrownBy(() -> new MarketIndexDailyHistoryRequest(
                        "KOSPI",
                        null,
                        TO_DATE
                ))
                .withMessage("fromDate must not be null.");
    }

    @Test
    void rejectsReversedDateRange() {
        assertThatThrownBy(() -> new MarketIndexDailyHistoryRequest(
                "KOSPI",
                TO_DATE,
                FROM_DATE
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fromDate must not be after toDate.");
    }
}
