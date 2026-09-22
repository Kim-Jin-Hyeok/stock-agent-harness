package com.stock.portfolio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.portfolio.initialization.StrategyPortfolioInitializer;
import com.stock.portfolio.persistence.PortfolioSnapshotJsonConverter;
import com.stock.portfolio.persistence.StrategyPortfolioRepository;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
        StrategyPortfolioInitializer initializer = mock(StrategyPortfolioInitializer.class);

        PortfolioSnapshot restored = store(initializer).getCurrentSnapshot(STRATEGY_IDENTITY);

        assertThat(restored).isEqualTo(expected);
        verifyNoInteractions(initializer);
    }

    @Test
    void savesInitialSnapshotWhenStrategyPortfolioDoesNotExist() {
        StrategyPortfolioInitializer initializer = mock(StrategyPortfolioInitializer.class);
        PortfolioSnapshot initialSnapshot = initialSnapshot();
        when(initializer.initialize(STRATEGY_IDENTITY)).thenReturn(initialSnapshot);

        PortfolioSnapshot snapshot = store(initializer).getCurrentSnapshot(STRATEGY_IDENTITY);

        assertThat(snapshot).isEqualTo(initialSnapshot);
        assertThat(repository.count()).isEqualTo(1L);
        verify(initializer).initialize(STRATEGY_IDENTITY);
    }

    @Test
    void resetReplacesSnapshotWithInitializerResult() {
        store().update(
                STRATEGY_IDENTITY,
                new PortfolioSnapshot(9_000_000L, 10_000_000L, List.of())
        );
        StrategyPortfolioInitializer initializer = mock(StrategyPortfolioInitializer.class);
        PortfolioSnapshot initialSnapshot = initialSnapshot();
        when(initializer.initialize(STRATEGY_IDENTITY)).thenReturn(initialSnapshot);

        PortfolioSnapshot reset = store(initializer).reset(STRATEGY_IDENTITY);

        assertThat(reset).isEqualTo(initialSnapshot);
        assertThat(store().getCurrentSnapshot(STRATEGY_IDENTITY)).isEqualTo(initialSnapshot);
        verify(initializer).initialize(STRATEGY_IDENTITY);
    }

    private PortfolioSnapshotStore store() {
        return store(new StrategyPortfolioInitializer());
    }

    private PortfolioSnapshotStore store(StrategyPortfolioInitializer initializer) {
        return new PortfolioSnapshotStore(
                repository,
                new PortfolioSnapshotJsonConverter(
                        new ObjectMapper().findAndRegisterModules()
                ),
                initializer
        );
    }

    private PortfolioSnapshot initialSnapshot() {
        return new PortfolioSnapshot(
                10_000_000L,
                10_000_000L,
                List.of()
        );
    }
}
