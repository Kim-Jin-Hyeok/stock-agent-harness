package com.stock.portfolio;

import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class PortfolioSnapshotStore {
    private final ConcurrentMap<InvestmentStrategyIdentity, PortfolioSnapshot> snapshots =
            new ConcurrentHashMap<>();

    public PortfolioSnapshot getCurrentSnapshot(InvestmentStrategyIdentity strategyIdentity) {
        Objects.requireNonNull(strategyIdentity, "strategyIdentity must not be null.");
        return snapshots.computeIfAbsent(strategyIdentity, ignored -> initialSnapshot());
    }

    public void update(
            InvestmentStrategyIdentity strategyIdentity,
            PortfolioSnapshot snapshot
    ) {
        snapshots.put(
                Objects.requireNonNull(strategyIdentity, "strategyIdentity must not be null."),
                Objects.requireNonNull(snapshot, "snapshot must not be null.")
        );
    }

    public PortfolioSnapshot reset(InvestmentStrategyIdentity strategyIdentity) {
        Objects.requireNonNull(strategyIdentity, "strategyIdentity must not be null.");
        PortfolioSnapshot snapshot = initialSnapshot();
        snapshots.put(strategyIdentity, snapshot);
        return snapshot;
    }

    public void resetAll() {
        snapshots.clear();
    }

    private PortfolioSnapshot initialSnapshot() {
        return new PortfolioSnapshot(
                10_000_000L,
                10_000_000L,
                List.of()
        );
    }
}
