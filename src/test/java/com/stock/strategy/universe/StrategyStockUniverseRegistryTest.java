package com.stock.strategy.universe;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.universe.config.ConfiguredStrategyStockUniverse;
import com.stock.strategy.universe.config.StrategyStockUniverseProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyStockUniverseRegistryTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );

    private final StrategyStockUniverseRegistry registry =
            new StrategyStockUniverseRegistry(
                    new StrategyStockUniverseProperties(List.of(
                            new ConfiguredStrategyStockUniverse(
                                    STRATEGY_IDENTITY.strategyId(),
                                    STRATEGY_IDENTITY.strategyVersion(),
                                    STRATEGY_IDENTITY.horizon(),
                                    List.of("005930", "000660")
                            )
                    ))
            );

    @Test
    void returnsCandidateSymbolsForStrategyIdentity() {
        List<String> result = registry.getCandidateSymbols(
                STRATEGY_IDENTITY
        );

        assertThat(result).containsExactly("005930", "000660");
        assertThatThrownBy(() -> result.add("035420"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsUnregisteredStrategyIdentity() {
        InvestmentStrategyIdentity unknown = new InvestmentStrategyIdentity(
                "SWING_V1",
                1,
                InvestmentHorizon.SWING
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> registry.getCandidateSymbols(unknown))
                .withMessage("Strategy stock universe not found: " + unknown);
    }
}
