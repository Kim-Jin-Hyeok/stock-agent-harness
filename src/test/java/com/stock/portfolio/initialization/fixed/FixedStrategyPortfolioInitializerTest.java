package com.stock.portfolio.initialization.fixed;

import com.stock.portfolio.PortfolioSnapshot;
import com.stock.portfolio.initialization.StrategyPortfolioInitializer;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class FixedStrategyPortfolioInitializerTest {
    private final StrategyPortfolioInitializer initializer =
            new FixedStrategyPortfolioInitializer();

    @Test
    void createsTemporaryInitialSnapshotForStrategy() {
        InvestmentStrategyIdentity strategyIdentity =
                new InvestmentStrategyIdentity(
                        "DAY_TRADING_V1",
                        1,
                        InvestmentHorizon.DAY_TRADING
                );

        PortfolioSnapshot snapshot = initializer.initialize(strategyIdentity);

        assertThat(snapshot.cashAmountKrw()).isEqualTo(10_000_000L);
        assertThat(snapshot.totalAssetAmountKrw()).isEqualTo(10_000_000L);
        assertThat(snapshot.positions()).isEmpty();
    }

    @Test
    void rejectsNullStrategyIdentity() {
        assertThatNullPointerException()
                .isThrownBy(() -> initializer.initialize(null))
                .withMessage("strategyIdentity must not be null.");
    }
}
