package com.stock.strategy.universe.config;

import com.stock.strategy.profile.InvestmentHorizon;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StrategyStockUniversePropertiesTest {

    @Test
    void copiesConfiguredSymbols() {
        List<String> symbols = new ArrayList<>(List.of("005930"));

        ConfiguredStrategyStockUniverse universe = universe(symbols);
        symbols.add("000660");

        assertThat(universe.symbols()).containsExactly("005930");
    }

    @Test
    void rejectsEmptySymbols() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> universe(List.of()))
                .withMessage(
                        "Strategy stock universe symbols must not be empty."
                );
    }

    @Test
    void rejectsBlankSymbol() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> universe(List.of(" ")))
                .withMessage(
                        "Strategy stock universe symbol must not be blank."
                );
    }

    @Test
    void rejectsDuplicateSymbol() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> universe(List.of("005930", "005930")))
                .withMessage(
                        "Duplicate strategy stock universe symbol: 005930"
                );
    }

    @Test
    void rejectsDuplicateStrategyIdentity() {
        ConfiguredStrategyStockUniverse universe = universe(
                List.of("005930")
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StrategyStockUniverseProperties(
                        List.of(universe, universe)
                ))
                .withMessageStartingWith(
                        "Duplicate strategy stock universe:"
                );
    }

    private ConfiguredStrategyStockUniverse universe(List<String> symbols) {
        return new ConfiguredStrategyStockUniverse(
                "DAY_TRADING_V1",
                1,
                InvestmentHorizon.DAY_TRADING,
                symbols
        );
    }
}
