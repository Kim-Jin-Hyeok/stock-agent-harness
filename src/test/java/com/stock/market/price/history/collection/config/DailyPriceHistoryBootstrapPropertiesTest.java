package com.stock.market.price.history.collection.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DailyPriceHistoryBootstrapPropertiesTest {

    @Test
    void copiesConfiguredSymbols() {
        List<String> symbols = new ArrayList<>(List.of("005930"));

        DailyPriceHistoryBootstrapProperties properties = properties(symbols);
        symbols.add("000660");

        assertThat(properties.symbols()).containsExactly("005930");
    }

    @Test
    void rejectsEmptySymbols() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(List.of()))
                .withMessage(
                        "Daily price history bootstrap symbols must not be empty."
                );
    }

    @Test
    void rejectsBlankSymbol() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(List.of(" ")))
                .withMessage(
                        "Daily price history bootstrap symbol must not be blank."
                );
    }

    @Test
    void rejectsDuplicateSymbol() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(List.of("005930", "005930")))
                .withMessage(
                        "Duplicate daily price history bootstrap symbol: 005930"
                );
    }

    @Test
    void rejectsNonPositiveInitialLookbackYears() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new DailyPriceHistoryBootstrapProperties(
                                true,
                                List.of("005930"),
                                0
                        ))
                .withMessage("initialLookbackYears must be positive.");
    }

    private DailyPriceHistoryBootstrapProperties properties(
            List<String> symbols
    ) {
        return new DailyPriceHistoryBootstrapProperties(
                true,
                symbols,
                3
        );
    }
}
