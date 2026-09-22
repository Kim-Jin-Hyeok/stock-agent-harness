package com.stock.portfolio.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.portfolio.PortfolioSnapshotStore;
import com.stock.portfolio.persistence.PortfolioSnapshotJsonConverter;
import com.stock.portfolio.persistence.StrategyPortfolioEntity;
import com.stock.portfolio.persistence.StrategyPortfolioRepository;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class PortfolioSnapshotStoreFixture {

    private PortfolioSnapshotStoreFixture() {
    }

    public static PortfolioSnapshotStore create() {
        Map<InvestmentStrategyIdentity, StrategyPortfolioEntity> entities = new HashMap<>();
        StrategyPortfolioRepository repository = mock(StrategyPortfolioRepository.class);

        when(repository.findByStrategyIdAndStrategyVersionAndHorizon(any(), anyInt(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(entities.get(
                        new InvestmentStrategyIdentity(
                                invocation.getArgument(0),
                                invocation.getArgument(1),
                                invocation.getArgument(2)
                        )
                )));
        when(repository.save(any(StrategyPortfolioEntity.class))).thenAnswer(invocation -> {
            StrategyPortfolioEntity entity = invocation.getArgument(0);
            entities.put(entity.strategyIdentity(), entity);
            return entity;
        });
        doAnswer(invocation -> {
            entities.clear();
            return null;
        }).when(repository).deleteAll();

        return new PortfolioSnapshotStore(
                repository,
                new PortfolioSnapshotJsonConverter(
                        new ObjectMapper().findAndRegisterModules()
                )
        );
    }
}
