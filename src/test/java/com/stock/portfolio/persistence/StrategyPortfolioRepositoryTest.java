package com.stock.portfolio.persistence;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class StrategyPortfolioRepositoryTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity("DAY_TRADING_V1", 1, InvestmentHorizon.DAY_TRADING);

    @Autowired
    private StrategyPortfolioRepository repository;

    @Test
    void findsPortfolioByCompleteStrategyIdentity() {
        repository.save(StrategyPortfolioEntity.of(STRATEGY_IDENTITY, "{\"positions\":[]}"));

        Optional<StrategyPortfolioEntity> result =
                repository.findByStrategyIdAndStrategyVersionAndHorizon(
                        STRATEGY_IDENTITY.strategyId(),
                        STRATEGY_IDENTITY.strategyVersion(),
                        STRATEGY_IDENTITY.horizon()
                );

        assertThat(result).isPresent();
        assertThat(result.get().strategyIdentity()).isEqualTo(STRATEGY_IDENTITY);
        assertThat(result.get().getSnapshotJson()).isEqualTo("{\"positions\":[]}");
    }

    @Test
    void enforcesUniqueStrategyIdentity() {
        repository.saveAndFlush(StrategyPortfolioEntity.of(STRATEGY_IDENTITY, "{}"));

        assertThatThrownBy(() -> repository.saveAndFlush(
                StrategyPortfolioEntity.of(STRATEGY_IDENTITY, "{}")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
