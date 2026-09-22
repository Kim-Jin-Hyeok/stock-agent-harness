package com.stock.portfolio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.portfolio.persistence.PortfolioSnapshotJsonConverter;
import com.stock.portfolio.persistence.StrategyPortfolioRepository;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PortfolioSnapshotStoreTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity("DAY_TRADING_V1", 1, InvestmentHorizon.DAY_TRADING);

    @Autowired
    private StrategyPortfolioRepository repository;

    @Test
    void restoresSnapshotFromRepositoryWithNewStoreInstance() {
        PortfolioSnapshot expected = new PortfolioSnapshot(
                9_300_000L,
                10_000_000L,
                List.of(new PortfolioPosition("005930", 10L, 70_000L, 700_000L))
        );
        store().update(STRATEGY_IDENTITY, expected);

        PortfolioSnapshot restored = store().getCurrentSnapshot(STRATEGY_IDENTITY);

        assertThat(restored).isEqualTo(expected);
    }

    @Test
    void savesInitialSnapshotWhenStrategyPortfolioDoesNotExist() {
        PortfolioSnapshot snapshot = store().getCurrentSnapshot(STRATEGY_IDENTITY);

        assertThat(snapshot.cashAmountKrw()).isEqualTo(10_000_000L);
        assertThat(snapshot.totalAssetAmountKrw()).isEqualTo(10_000_000L);
        assertThat(snapshot.positions()).isEmpty();
        assertThat(repository.count()).isEqualTo(1L);
    }

    private PortfolioSnapshotStore store() {
        return new PortfolioSnapshotStore(
                repository,
                new PortfolioSnapshotJsonConverter(
                        new ObjectMapper().findAndRegisterModules()
                )
        );
    }
}
