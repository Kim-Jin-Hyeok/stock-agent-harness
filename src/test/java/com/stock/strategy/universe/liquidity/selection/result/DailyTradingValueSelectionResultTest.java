package com.stock.strategy.universe.liquidity.selection.result;

import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyTradingValueSelectionResultTest {
    private static final LocalDate SELECTION_DATE = LocalDate.of(2026, 9, 23);
    private final DailyTradingValueAverage average = new DailyTradingValueAverage(
            "005930", SELECTION_DATE, List.of(SELECTION_DATE),
            TradingVenueScope.INTEGRATED, BigInteger.valueOf(300L)
    );

    @ParameterizedTest
    @EnumSource(DailyTradingValueSelectionStatus.class)
    void preservesAverageRankAndStatus(DailyTradingValueSelectionStatus status) {
        DailyTradingValueSelectionResult result = new DailyTradingValueSelectionResult(
                average, 1, status
        );

        assertThat(result.average()).isSameAs(average);
        assertThat(result.rank()).isEqualTo(1);
        assertThat(result.status()).isEqualTo(status);
    }

    @Test
    void rejectsNullAverage() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionResult(
                null, 1, DailyTradingValueSelectionStatus.SELECTED
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("average must not be null.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsNonPositiveRank(int rank) {
        assertThatThrownBy(() -> new DailyTradingValueSelectionResult(
                average, rank, DailyTradingValueSelectionStatus.SELECTED
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rank must be positive.");
    }

    @Test
    void rejectsNullStatus() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionResult(average, 1, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status must not be null.");
    }
}
