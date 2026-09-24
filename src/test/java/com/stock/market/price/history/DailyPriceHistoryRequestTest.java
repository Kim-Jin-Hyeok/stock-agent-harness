package com.stock.market.price.history;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyPriceHistoryRequestTest {
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 9, 23);

    @Test
    void createsRequestWithValidDateRange() {
        DailyPriceHistoryRequest request = new DailyPriceHistoryRequest(
                "005930",
                FROM_DATE,
                TO_DATE
        );

        assertThat(request.symbol()).isEqualTo("005930");
        assertThat(request.fromDate()).isEqualTo(FROM_DATE);
        assertThat(request.toDate()).isEqualTo(TO_DATE);
    }

    @Test
    void allowsSameFromAndToDate() {
        DailyPriceHistoryRequest request = new DailyPriceHistoryRequest(
                "005930",
                FROM_DATE,
                FROM_DATE
        );

        assertThat(request.fromDate()).isEqualTo(request.toDate());
    }

    @Test
    void rejectsBlankSymbol() {
        assertThatThrownBy(() -> new DailyPriceHistoryRequest(
                " ",
                FROM_DATE,
                TO_DATE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must not be blank.");
    }

    @Test
    void rejectsNullDates() {
        assertThatThrownBy(() -> new DailyPriceHistoryRequest(
                "005930",
                null,
                TO_DATE
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("fromDate must not be null.");
        assertThatThrownBy(() -> new DailyPriceHistoryRequest(
                "005930",
                FROM_DATE,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("toDate must not be null.");
    }

    @Test
    void rejectsFromDateAfterToDate() {
        assertThatThrownBy(() -> new DailyPriceHistoryRequest(
                "005930",
                TO_DATE,
                FROM_DATE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fromDate must not be after toDate.");
    }
}
