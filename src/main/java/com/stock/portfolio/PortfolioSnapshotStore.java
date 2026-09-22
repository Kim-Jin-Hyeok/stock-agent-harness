package com.stock.portfolio;

import com.stock.portfolio.initialization.StrategyPortfolioInitializer;
import com.stock.portfolio.persistence.PortfolioSnapshotJsonConverter;
import com.stock.portfolio.persistence.StrategyPortfolioEntity;
import com.stock.portfolio.persistence.StrategyPortfolioRepository;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PortfolioSnapshotStore {
    private final StrategyPortfolioRepository strategyPortfolioRepository;
    private final PortfolioSnapshotJsonConverter portfolioSnapshotJsonConverter;
    private final StrategyPortfolioInitializer strategyPortfolioInitializer;

    @Transactional
    public PortfolioSnapshot getCurrentSnapshot(InvestmentStrategyIdentity strategyIdentity) {
        Objects.requireNonNull(strategyIdentity, "strategyIdentity must not be null.");

        return findByStrategyIdentity(strategyIdentity)
                .map(entity -> portfolioSnapshotJsonConverter.fromJson(entity.getSnapshotJson()))
                .orElseGet(() -> saveInitialSnapshot(strategyIdentity));
    }

    @Transactional
    public void update(
            InvestmentStrategyIdentity strategyIdentity,
            PortfolioSnapshot snapshot
    ) {
        Objects.requireNonNull(strategyIdentity, "strategyIdentity must not be null.");
        Objects.requireNonNull(snapshot, "snapshot must not be null.");

        String snapshotJson = portfolioSnapshotJsonConverter.toJson(snapshot);
        StrategyPortfolioEntity entity = findByStrategyIdentity(strategyIdentity)
                .orElseGet(() -> StrategyPortfolioEntity.of(strategyIdentity, snapshotJson));
        entity.updateSnapshotJson(snapshotJson);
        strategyPortfolioRepository.save(entity);
    }

    @Transactional
    public PortfolioSnapshot reset(InvestmentStrategyIdentity strategyIdentity) {
        Objects.requireNonNull(strategyIdentity, "strategyIdentity must not be null.");
        PortfolioSnapshot snapshot = strategyPortfolioInitializer.initialize(strategyIdentity);
        update(strategyIdentity, snapshot);
        return snapshot;
    }

    @Transactional
    public void resetAll() {
        strategyPortfolioRepository.deleteAll();
    }

    private Optional<StrategyPortfolioEntity> findByStrategyIdentity(
            InvestmentStrategyIdentity strategyIdentity
    ) {
        return strategyPortfolioRepository.findByStrategyIdAndStrategyVersionAndHorizon(
                strategyIdentity.strategyId(),
                strategyIdentity.strategyVersion(),
                strategyIdentity.horizon()
        );
    }

    private PortfolioSnapshot saveInitialSnapshot(
            InvestmentStrategyIdentity strategyIdentity
    ) {
        PortfolioSnapshot snapshot = strategyPortfolioInitializer.initialize(strategyIdentity);
        strategyPortfolioRepository.save(StrategyPortfolioEntity.of(
                strategyIdentity,
                portfolioSnapshotJsonConverter.toJson(snapshot)
        ));
        return snapshot;
    }
}
