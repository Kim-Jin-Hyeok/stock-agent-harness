package com.stock.market.price.history.collection.config;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DailyPriceHistoryCollectionPropertiesTest {

    @Test
    void acceptsDailyBarAvailableAt() {
        DailyPriceHistoryCollectionProperties properties =
                properties(List.of("005930"));

        assertThat(properties.dailyBarAvailableAt())
                .isEqualTo(LocalTime.of(20, 10));
        assertThat(properties.symbols()).containsExactly("005930");
        assertThat(properties.initialLookbackYears()).isEqualTo(3);
    }

    @Test
    void copiesConfiguredSymbols() {
        List<String> symbols = new ArrayList<>(List.of("005930"));

        DailyPriceHistoryCollectionProperties properties =
                properties(symbols);
        symbols.add("000660");

        assertThat(properties.symbols()).containsExactly("005930");
    }

    @Test
    void rejectsNullDailyBarAvailableAt() {
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new DailyPriceHistoryCollectionProperties(
                                null,
                                List.of("005930"),
                                3
                        ))
                .withMessage("dailyBarAvailableAt must not be null.");
    }

    @Test
    void rejectsEmptySymbols() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(List.of()))
                .withMessage(
                        "Daily price history collection symbols must not be empty."
                );
    }

    @Test
    void rejectsBlankSymbol() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(List.of(" ")))
                .withMessage(
                        "Daily price history collection symbol must not be blank."
                );
    }

    @Test
    void rejectsDuplicateSymbol() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(List.of("005930", "005930")))
                .withMessage(
                        "Duplicate daily price history collection symbol: 005930"
                );
    }

    @Test
    void rejectsNonPositiveInitialLookbackYears() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new DailyPriceHistoryCollectionProperties(
                                LocalTime.of(20, 10),
                                List.of("005930"),
                                0
                        ))
                .withMessage("initialLookbackYears must be positive.");
    }

    private DailyPriceHistoryCollectionProperties properties(
            List<String> symbols
    ) {
        return new DailyPriceHistoryCollectionProperties(
                LocalTime.of(20, 10),
                symbols,
                3
        );
    }
}
